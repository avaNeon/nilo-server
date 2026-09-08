-- 更新冷表
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
            -- 使用ZADD直接赋值，而不是ZINCRBY，这是为了避免以下情况重复记录冷表播放数
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
