package com.neon.niloweb.task;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.niloweb.config.SystemConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class VideoOnlineCleanUpTask
{
    private final RedisTemplate <String, Object> redisTemplate;

    private final SystemConfig systemConfig;

    // 每30秒执行一次清理
    @Scheduled(fixedRateString = "#{@systemConfig.onlineCountCleanUpTimeMs}")
    public void cleanUp()
    {
        // 1. 获取当前有活跃用户的视频ID集合
        Set <Object> redisResult = redisTemplate.opsForSet().members(RedisKey.VIDEO_ACTIVE_LIST);
        if (redisResult == null || redisResult.isEmpty())
        {
            return;
        }

        Set <String> activeVideoSet = redisResult.stream().map(Object::toString).collect(Collectors.toSet());

        long minValidTimestamp = System.currentTimeMillis() - systemConfig.getOnlineExpireTimeMs();

        for (String key : activeVideoSet)
        {
            // 2. 清除当前视频下超时（Score小于minValidTime）的用户
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, minValidTimestamp - 1);
            // 3. 如果清理后这个视频没人看了，把它从活跃列表中移除，并删除对应的ZSet key释放内存
            Long currentCount = redisTemplate.opsForZSet().zCard(key);
            if (currentCount == null || currentCount == 0)
            {
                redisTemplate.delete(key);
                redisTemplate.opsForSet().remove(RedisKey.VIDEO_ACTIVE_LIST, key);
            }
        }
    }
}
