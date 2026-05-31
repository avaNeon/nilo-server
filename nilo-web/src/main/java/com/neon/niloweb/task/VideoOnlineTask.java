package com.neon.niloweb.task;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.niloweb.config.SystemConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Component
public class VideoOnlineTask
{
    private final RedisTemplate <String, Object> redisTemplate;

    private final StringRedisTemplate stringRedisTemplate;

    private final SystemConfig systemConfig;

    private final int batchSize = 1000;

    private static final String CLEAN_UP_SCRIPT = """
            local active_list_key = KEYS[1]
            local min_valid_timestamp = tonumber(ARGV[1])
            
            if not min_valid_timestamp then
                error('missing required arguments')
            end
            
            local cleaned_count = 0
            local deleted_count = 0
            local failed_count = 0
            
            for i = 2, #ARGV do
                local key = ARGV[i]
            
                local ok, result = pcall(function()
                    local removed = redis.call('ZREMRANGEBYSCORE', key, 0, min_valid_timestamp - 1)
                    cleaned_count = cleaned_count + removed
            
                    local current_count = redis.call('ZCARD', key)
                    if current_count == 0 then
                        redis.call('DEL', key)
                        redis.call('SREM', active_list_key, key)
                        deleted_count = deleted_count + 1
                    end
                end)
            
                if not ok then
                    failed_count = failed_count + 1
                    redis.log(redis.LOG_WARNING, 'failed to clean online video key: ' .. key)
                end
            end
            
            return cleaned_count .. ':' .. deleted_count .. ':' .. failed_count
            """;

    @Scheduled(fixedRateString = "#{@systemConfig.onlineCountCleanUpTimeMs}")
    public void cleanUp()
    {
        // 1. 获取当前有活跃用户的视频ID集合
        Set <Object> redisResult = redisTemplate.opsForSet().members(RedisKey.VIDEO_ACTIVE_LIST);
        if (redisResult == null || redisResult.isEmpty())
        {
            return;
        }

        List <String> activeVideoList = redisResult.stream().map(Object::toString).toList();

        int size = activeVideoList.size();

        // 分批清理
        for (int start = 0 ; start < size ; start += batchSize)
        {
            long minValidTimestamp = System.currentTimeMillis() - systemConfig.getOnlineExpireTimeMs();

            int end = Math.min(start + batchSize, activeVideoList.size());

            List <String> batch = activeVideoList.subList(start, end);

            cleanUpBatch(minValidTimestamp, batch);
        }
    }

    private void cleanUpBatch(long minValidTimestamp, List <String> keys)
    {
        DefaultRedisScript <String> script = new DefaultRedisScript <>();
        script.setScriptText(CLEAN_UP_SCRIPT);
        script.setResultType(String.class);

        List <Object> args = new ArrayList <>(keys.size() + 1);
        args.add(String.valueOf(minValidTimestamp));
        args.addAll(keys);

        stringRedisTemplate.execute(script, List.of(RedisKey.VIDEO_ACTIVE_LIST), args.toArray(Object[]::new));
    }
}
