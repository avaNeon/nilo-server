-- redis key 名称
local hot_ranking_key = KEYS[1]
local hot_counting_key_prefix = KEYS[2]
local cold_ranking_key = KEYS[3]
local cold_counting_key_prefix = KEYS[4]
local video_record_prefix = KEYS[5]

-- 参数
local currentHour = tonumber(ARGV[1])
local currentDay = ARGV[2]

local success_count = 0
local failed_count = 0

-- 批量新增播放数据
for i = 3, #ARGV, 2 do
    local video_id = ARGV[i]
    local increment = tonumber(ARGV[i + 1])

    if video_id and increment and increment > 0 then

        local ok, result = pcall(function()

            local hotRank = redis.call('ZRANK', hot_ranking_key, video_id)
            local coldRank = redis.call('ZRANK', cold_ranking_key, video_id)

            if hotRank then -- 如果热表中有记录
                -- 热表记录增加
                redis.call('ZINCRBY', hot_counting_key_prefix .. video_id, increment, currentHour)

            elseif coldRank then -- 如果冷表中有记录
                -- 冷表记录增加
                redis.call('ZINCRBY', cold_counting_key_prefix .. video_id, increment, currentHour)

            else -- 如果两个排行榜中无记录，冷表加上一条
                redis.call('ZADD', cold_ranking_key, increment, video_id)
                redis.call('ZINCRBY', cold_counting_key_prefix .. video_id, increment, currentHour)
            end

            -- 记录历史播放记录，保存两天
            local video_record_key = video_record_prefix .. currentDay
            local exist = redis.call('EXISTS', video_record_key) == 1
            local new_count = redis.call('HINCRBY', video_record_key, video_id, increment)

            -- 如果第一次创建key，设置2天的过期时间
            if not exist then
                redis.call('EXPIRE', video_record_key, 172800)
            end

        end)

        if not ok then
            failed_count = failed_count + 1
            redis.log(redis.LOG_WARNING, 'failed to add play count to a video: ' .. video_id)
        else
            success_count = success_count + 1
        end
    end
end

return success_count .. ':' .. failed_count
