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
