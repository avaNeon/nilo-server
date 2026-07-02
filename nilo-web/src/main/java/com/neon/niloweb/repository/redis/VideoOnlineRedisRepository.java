package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.niloweb.config.WebConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class VideoOnlineRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    private final WebConfig webConfig;

    /**
     * 只查询时间戳与当前时间间隔在 expire time 内的数据（其余数据逻辑上过期）
     *
     * @param videoIdAndFileIndex
     * @return
     */
    public long getOnlineCountFromRedis(String videoIdAndFileIndex)
    {
        String key = RedisKey.VIDEO_HEARTBEAT_PREFIX + videoIdAndFileIndex;
        long currentTime = System.currentTimeMillis();
        long minValidTime = currentTime - webConfig.getOnlineExpireTimeMs();
        // O(log(N))
        Long count = redisTemplate.opsForZSet().count(key, minValidTime, currentTime);
        return count != null ? count : 0;
    }
}
