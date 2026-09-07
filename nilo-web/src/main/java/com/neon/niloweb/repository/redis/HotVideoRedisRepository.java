package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.niloweb.config.WebConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Repository
public class HotVideoRedisRepository
{

    private final RedisTemplate <String, Object> redisTemplate;

    private final StringRedisTemplate stringRedisTemplate;

    private final WebConfig webConfig;

    /**
     * 获取热门视频id列表
     *
     * @param pageNo 页号
     * @return 热门视频列表
     */
    public List <Long> getHotVideoIdList(int pageNo)
    {
        int pageSize = webConfig.getHotVideoPageSize();
        int start = (pageNo - 1) * pageSize;

        Set <Object> result = redisTemplate.opsForZSet().reverseRange(RedisKey.HOT_VIDEO_RANKING, start, start + pageSize - 1);

        if (result == null || result.isEmpty())
        {
            return List.of();
        }
        else
        {
            return result.stream().filter(Objects::nonNull).map(Object::toString).map(Long::valueOf).toList();
        }
    }

    /**
     * <b>更新冷表视频统计信息</b><hr/>
     */
    public void refreshColdCount()
    {
        String luaScript = """
                 -- redis key 名称
                 local cold_ranking_key = KEYS[1]
                 local cold_counting_key_prefix = KEYS[2]
                 local hot_ranking_key = KEYS[3]
                 local hot_counting_key_prefix = KEYS[4]
                
                 -- 参数
                 local current_hour = tonumber(ARGV[1])
                 local window_start_hour = tonumber(ARGV[2])
                 local upgrade_threshold = tonumber(ARGV[3])
                
                 -- 如果没有所需参数，报错
                 if not current_hour or not window_start_hour or not upgrade_threshold then
                     error('missing required arguments')
                 end
                
                 -- 统计本次操作中移除的记录数，晋级的记录数，刷新的记录数，错误数，并在最后返回
                 local removed_count = 0
                 local upgraded_count = 0
                 local refreshed_count = 0
                 local failed_count = 0
                
                 -- 刷新指定 video_id 播放数据
                 -- 会清理没有播放数的记录，更新播放数，并根据播放数晋级
                 for i = 4, #ARGV do
                     local video_id = ARGV[i]
                     local cold_counting_key = cold_counting_key_prefix .. video_id
                     local hot_counting_key = hot_counting_key_prefix .. video_id
                
                     -- 总播放数
                     local score = 0
                
                     -- 执行修改表的操作，如果失败，换下一条记录
                     local ok, result = pcall(function()
                
                         -- 取出所有桶
                         local buckets = redis.call('ZRANGE', cold_counting_key, 0, -1, 'WITHSCORES')
                
                         -- 移除所有过期的桶，并统计播放记录
                         -- 冷表统计 [currentHour - 1,current_hour] 部分的小时桶
                         for index = 1, #buckets, 2 do
                
                             local bucket = buckets[index]
                             local bucket_hour = tonumber(bucket)
                             local bucket_score = tonumber(buckets[index + 1])
                
                             if bucket_hour and bucket_hour < window_start_hour then
                                 redis.call('ZREM', cold_counting_key, bucket)
                             elseif bucket_hour and bucket_hour >= window_start_hour and bucket_hour <= current_hour then
                                 score = score + bucket_score
                             end
                
                         end
                
                         -- 晋级
                         if score >= upgrade_threshold then
                
                             -- 先迁移ranking，counting迁移失败也会被自动删除
                             redis.call('ZADD', hot_ranking_key, score, video_id)
                
                             -- 晋级时迁移 cold count 中还保留的桶，包括当前小时桶
                             local remaining_buckets = redis.call('ZRANGE', cold_counting_key, 0, -1, 'WITHSCORES')
                
                             -- 迁移 counting
                             -- 结果中混合了成员名[1]和分数[2]，所以步长为2
                             -- 使用ZADD直接赋值，这是为了避免以下情况：
                             -- 当冷表升级到热表，但是下一次更新时热表降级到冷表时，如果冷表在升级时删除旧数据失败，热表会重复记录冷表播放数
                             -- 以上情况可能会很可怕，可能会导致数据总是在冷表和热表之间来回切换，造成垃圾数据
                             for index = 1, #remaining_buckets, 2 do
                                 redis.call('ZADD', hot_counting_key, tonumber(remaining_buckets[index + 1]), remaining_buckets[index])
                             end
                
                             -- 删除冷表数据
                             -- 先删除统计记录，这样就算没删除冷表RANKING记录，也可以后续自动更新因为统计记录为0而删除
                             redis.call('DEL', cold_counting_key)
                             redis.call('ZREM', cold_ranking_key, video_id)
                
                             upgraded_count = upgraded_count + 1
                
                         else -- 未晋级
                             -- 查一下冷表是否还有数据，现在应该没有过期的数据了
                             local remaining_count = redis.call('ZCARD', cold_counting_key)
                
                             -- 如果没有数据，移除
                             if remaining_count == 0 then
                                 redis.call('ZREM', cold_ranking_key, video_id)
                                 removed_count = removed_count + 1
                             else -- 如果有数据，给 ranking 赋值新播放数
                                 redis.call('ZADD', cold_ranking_key, score, video_id)
                                 refreshed_count = refreshed_count + 1
                             end
                         end
                
                     end)
                
                     if not ok then
                         failed_count = failed_count + 1
                         redis.log(redis.LOG_WARNING, 'failed to refresh video from cold table: ' .. video_id)
                     end
                 end
                
                 return removed_count .. ':' .. upgraded_count .. ':' .. refreshed_count .. ':' .. failed_count
                """;

        long currentHour = System.currentTimeMillis() / 1000 / 3600;
        long windowStartHour = currentHour - webConfig.getColdVideoTimeDistance();
        int batchSize = 1000;

        // 使用ZSCAN，创建一个cursor，迭代冷表ranking记录
        // ZSCAN可能返回重复元素，我们的逻辑有幂等性，不会受到影响
        try (Cursor <ZSetOperations.TypedTuple <String>> cursor = stringRedisTemplate.opsForZSet()
                                                                                     .scan(RedisKey.COLD_VIDEO_RANKING,
                                                                                           ScanOptions.scanOptions()
                                                                                                      .count(batchSize)
                                                                                                      .build()))
        {
            List <String> videoIds = new ArrayList <>(batchSize);

            while (cursor.hasNext())
            {
                ZSetOperations.TypedTuple <String> tuple = cursor.next();
                String videoId = tuple.getValue();

                if (videoId == null)
                {
                    continue;
                }

                videoIds.add(videoId);

                // 每当到达了足够的批处理数量，交给redis让其执行
                if (videoIds.size() >= batchSize)
                {
                    refreshColdCountBatch(luaScript, currentHour, windowStartHour, videoIds);
                    videoIds.clear();
                }
            }

            // 如果最后一批没到达批处理数量，那么通过这个代码执行剩下的数据更新
            if (!videoIds.isEmpty())
            {
                refreshColdCountBatch(luaScript, currentHour, windowStartHour, videoIds);
            }
        }
    }

    /**
     * <b>更新热表视频统计信息</b><hr/>
     */
    public void refreshHotCount()
    {
        String luaScript = """
                -- 更新热表
                -- redis key 名称
                local hot_ranking_key = KEYS[1]
                local hot_counting_key_prefix = KEYS[2]
                local cold_ranking_key = KEYS[3]
                local cold_counting_key_prefix = KEYS[4]
                
                -- 参数
                local current_hour = tonumber(ARGV[1])
                local window_start_hour = tonumber(ARGV[2])
                local cold_window_start_hour = tonumber(ARGV[3])
                local downgrade_threshold = tonumber(ARGV[4])
                
                -- 如果没有所需参数，报错
                if not current_hour or not window_start_hour or not cold_window_start_hour or not downgrade_threshold then
                    error('missing required arguments')
                end
                
                -- 统计本次操作中移除的记录数，降级的记录数，刷新的记录数，错误数，并在最后返回
                local removed_count = 0
                local downgraded_count = 0
                local refreshed_count = 0
                local failed_count = 0
                
                -- 刷新指定 video_id 播放数据
                -- 会清理没有播放数的记录，更新播放数，并根据播放数降级
                for i = 5, #ARGV do
                    local video_id = ARGV[i]
                    local hot_counting_key = hot_counting_key_prefix .. video_id
                    local cold_counting_key = cold_counting_key_prefix .. video_id
                
                    local score = 0
                    local cold_score = 0
                
                    local ok, result = pcall(function()
                
                        local buckets = redis.call('ZRANGE', hot_counting_key, 0, -1, 'WITHSCORES')
                
                        -- 清理统计窗口之前的数据，同时统计 [currentHour - maxTimeInterval, currentHour] 的播放数
                        -- 当前小时桶会保留并参与本轮统计
                        for index = 1, #buckets, 2 do
                            local bucket = buckets[index]
                            local bucket_score = tonumber(buckets[index + 1])
                            local bucket_hour = tonumber(bucket)
                
                            if bucket_hour and bucket_hour < window_start_hour then
                                redis.call('ZREM', hot_counting_key, bucket)
                            elseif bucket_hour and bucket_hour <= current_hour then
                                score = score + bucket_score
                            end
                
                            -- 如果本视频播放记录降级到冷表，总播放数的值
                            if bucket_hour and bucket_hour == cold_window_start_hour then
                                cold_score = bucket_score
                            end
                        end
                
                        if score < downgrade_threshold then
                
                            -- 如果迁移到冷表总播放数是0，直接删除，不迁移
                            -- 这一步是为了防止冷表升级一半，但是没有把统计信息刷到热表，导致热表再降级到冷表，把冷表数据刷掉的问题
                            -- 也是为了防止热表到冷表删除一半，热表统计信息没有但是RANKING有记录，导致把空数据覆盖冷表数据的问题
                            if cold_score == 0 then
                                redis.call('DEL', hot_counting_key)
                                redis.call('ZREM', hot_ranking_key, video_id)
                                removed_count = removed_count + 1
                
                            else -- 如果还有数据，开始迁移
                
                                local remaining_buckets = redis.call('ZRANGE', hot_counting_key, 0, -1, 'WITHSCORES')
                
                                -- 先把 ranking 迁移到冷表，这样就算counting没迁移到，为0的数据也会在更新时删除
                                redis.call('ZADD', cold_ranking_key, cold_score, video_id)
                                downgraded_count = downgraded_count + 1
                
                                -- 把统计信息迁移到冷表
                                for index = 1, #remaining_buckets, 2 do
                                    local bucket = remaining_buckets[index]
                                    local bucket_score = tonumber(remaining_buckets[index + 1])
                                    local bucket_hour = tonumber(bucket)
                
                                    -- 降级时只迁移冷表可能继续使用的桶：上一小时桶和当前小时桶
                                    -- 使用ZADD直接赋值，这是为了避免这种情况：
                                    -- 如果热表降级到冷表失败，但是下一次更新冷表数据又升级到热表时，使用ZINCRBY会重复计算桶的播放数
                                    if bucket_hour and bucket_hour >= cold_window_start_hour then
                                        redis.call('ZADD', cold_counting_key, bucket_score, bucket)
                                    end
                                end
                
                                -- 删除热表信息，先删统计信息，这样就算没删除热表RANKING记录，也可以后续自动更新时删除
                                redis.call('DEL', hot_counting_key)
                                redis.call('ZREM', hot_ranking_key, video_id)
                
                            end
                        else -- 不降级，只更新 ranking 记录
                            redis.call('ZADD', hot_ranking_key, score, video_id)
                            refreshed_count = refreshed_count + 1
                        end
                    end)
                
                    if not ok then
                        failed_count = failed_count + 1
                        redis.log(redis.LOG_WARNING, 'failed to refresh video from hot table: ' .. video_id)
                    end
                
                end
                
                return removed_count .. ':' .. downgraded_count .. ':' .. refreshed_count .. ':' .. failed_count
                """;

        long currentHour = System.currentTimeMillis() / 1000 / 3600;
        long windowStartHour = currentHour - webConfig.getHotVideoTimeDistance();
        long coldWindowStartHour = currentHour - 1;
        int batchSize = 1000;

        // 使用SCAN，创建一个cursor，迭代热表ranking记录
        // SCAN可能返回重复元素，我们的逻辑有幂等性，不会受到影响
        try (Cursor <ZSetOperations.TypedTuple <String>> cursor = stringRedisTemplate.opsForZSet()
                                                                                     .scan(RedisKey.HOT_VIDEO_RANKING,
                                                                                           ScanOptions.scanOptions()
                                                                                                      .count(batchSize)
                                                                                                      .build()))
        {
            List <String> videoIds = new ArrayList <>(batchSize);

            while (cursor.hasNext())
            {
                ZSetOperations.TypedTuple <String> tuple = cursor.next();
                String videoId = tuple.getValue();

                if (videoId == null)
                {
                    continue;
                }

                videoIds.add(videoId);

                // 每当到达了足够的批处理数量，交给redis让其执行
                if (videoIds.size() >= batchSize)
                {
                    refreshHotCountBatch(luaScript, currentHour, windowStartHour, coldWindowStartHour, videoIds);
                    videoIds.clear();
                }
            }

            // 如果最后一批没到达批处理数量，那么通过这个代码执行剩下的数据更新
            if (!videoIds.isEmpty())
            {
                refreshHotCountBatch(luaScript, currentHour, windowStartHour, coldWindowStartHour, videoIds);
            }
        }
    }

    /**
     * <b>批量更新冷表视频统计信息</b><hr/>
     *
     * @param luaScript       lua脚本
     * @param currentHour     当前UTC整点小时数
     * @param windowStartHour 统计窗口开始小时数
     * @param videoIds        视频ID列表
     */
    private void refreshColdCountBatch(String luaScript, long currentHour, long windowStartHour, List <String> videoIds)
    {
        List <String> args = new ArrayList <>(videoIds.size() + 3);
        args.add(String.valueOf(currentHour));
        args.add(String.valueOf(windowStartHour));
        args.add(String.valueOf(webConfig.getUpgradeThreshold()));
        args.addAll(videoIds);

        executeLuaScript(luaScript,
                         List.of(RedisKey.COLD_VIDEO_RANKING,
                                 RedisKey.COLD_VIDEO_COUNTING_PREFIX,
                                 RedisKey.HOT_VIDEO_RANKING,
                                 RedisKey.HOT_VIDEO_COUNTING_PREFIX),
                         args);

    }

    /**
     * <b>批量更新热表视频统计信息</b><hr/>
     *
     * @param luaScript           lua脚本
     * @param currentHour         当前UTC整点小时数
     * @param windowStartHour     热表统计窗口开始小时数
     * @param coldWindowStartHour 冷表统计窗口开始小时数
     * @param videoIds            视频ID列表
     */
    private void refreshHotCountBatch(String luaScript,
                                      long currentHour,
                                      long windowStartHour,
                                      long coldWindowStartHour,
                                      List <String> videoIds)
    {
        List <String> args = new ArrayList <>(videoIds.size() + 4);
        args.add(String.valueOf(currentHour));
        args.add(String.valueOf(windowStartHour));
        args.add(String.valueOf(coldWindowStartHour));
        args.add(String.valueOf(webConfig.getDowngradeThreshold()));
        args.addAll(videoIds);

        executeLuaScript(luaScript,
                         List.of(RedisKey.HOT_VIDEO_RANKING,
                                 RedisKey.HOT_VIDEO_COUNTING_PREFIX,
                                 RedisKey.COLD_VIDEO_RANKING,
                                 RedisKey.COLD_VIDEO_COUNTING_PREFIX),
                         args);
    }

    /**
     * <b>在Redis中执行Lua脚本</b><hr/>
     *
     * @param luaScript lua脚本
     * @param keys      keys
     * @param args      args
     */
    private void executeLuaScript(String luaScript, List <String> keys, List <String> args)
    {
        // 构建命令和返回类型
        DefaultRedisScript <String> script = new DefaultRedisScript <>();
        script.setScriptText(luaScript);
        script.setResultType(String.class);

        // 执行并传入参数
        stringRedisTemplate.execute(script, keys, args.toArray(Object[]::new));
    }
}
