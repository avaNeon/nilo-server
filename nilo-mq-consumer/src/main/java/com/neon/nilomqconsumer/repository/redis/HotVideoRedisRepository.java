package com.neon.nilomqconsumer.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Repository
public class HotVideoRedisRepository
{
    private final StringRedisTemplate stringRedisTemplate;

    public void updateVideoPlayCountBatch(Map <Long, Integer> playCountMap)
    {
        if (playCountMap == null || playCountMap.isEmpty())
        {
            return;
        }

        String luaScript = """
                local hot_ranking_key = KEYS[1]
                local hot_counting_key_prefix = KEYS[2]
                local cold_ranking_key = KEYS[3]
                local cold_counting_key_prefix = KEYS[4]
                local video_record_prefix = KEYS[5]
                
                local currentHour = tonumber(ARGV[1])
                local currentDay = ARGV[2]
                
                local success_count = 0
                local failed_count = 0
                
                for i = 3, #ARGV, 2 do
                    local video_id = ARGV[i]
                    local increment = tonumber(ARGV[i + 1])
                
                    if video_id and increment and increment > 0 then
                        local ok, result = pcall(function()
                            local hotRank = redis.call('ZRANK', hot_ranking_key, video_id)
                            local coldRank = redis.call('ZRANK', cold_ranking_key, video_id)
                
                            if hotRank then
                                redis.call('ZINCRBY', hot_counting_key_prefix .. video_id, increment, currentHour)
                            elseif coldRank then
                                redis.call('ZINCRBY', cold_counting_key_prefix .. video_id, increment, currentHour)
                            else
                                redis.call('ZADD', cold_ranking_key, increment, video_id)
                                redis.call('ZINCRBY', cold_counting_key_prefix .. video_id, increment, currentHour)
                            end
                
                            local video_record_key = video_record_prefix .. currentDay
                            local exist = redis.call('EXISTS', video_record_key) == 1
                            redis.call('HINCRBY', video_record_key, video_id, increment)
                
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
                """;

        long currentHour = ChronoUnit.HOURS.between(Instant.EPOCH, Instant.now());
        String currentDay = LocalDate.now().toString();

        List <String> args = new ArrayList <>(playCountMap.size() * 2 + 2);
        args.add(String.valueOf(currentHour));
        args.add(currentDay);
        playCountMap.forEach((videoId, increment) ->
                             {
                                 if (videoId != null && increment != null && increment > 0)
                                 {
                                     args.add(String.valueOf(videoId));
                                     args.add(String.valueOf(increment));
                                 }
                             });

        if (args.size() == 2)
        {
            return;
        }

        DefaultRedisScript <String> script = new DefaultRedisScript <>();
        script.setScriptText(luaScript);
        script.setResultType(String.class);

        String result = stringRedisTemplate.execute(script,
                                                    List.of(RedisKey.HOT_VIDEO_RANKING,
                                                            RedisKey.HOT_VIDEO_COUNTING_PREFIX,
                                                            RedisKey.COLD_VIDEO_RANKING,
                                                            RedisKey.COLD_VIDEO_COUNTING_PREFIX,
                                                            RedisKey.VIDEO_DAILY_PLAY_COUNT_PREFIX),
                                                    args.toArray(Object[]::new));

        log.info("play count lua script executed result: {}", result);
    }
}
