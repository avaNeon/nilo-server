package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Repository
public class FileRedisRepository
{
    private final RedisTemplate <String, String> redisTemplate;

    /**
     * 获取预签名url缓存
     *
     * @param key 缓存的key
     * @return 如果存在，返回缓存
     */
    public String getPresignedUrlCache(String key)
    {
        String cacheKey = RedisKey.PRESIGNED_URL_CACHE_PREFIX + key;
        return redisTemplate.opsForValue().get(cacheKey);
    }

    /**
     * 添加预签名url缓存
     *
     * @param key           缓存的key
     * @param presignedUrl  预签名url
     * @param expireSeconds 过期时间（秒）
     */
    public void setPresignedUrlCache(String key, String presignedUrl, int expireSeconds)
    {
        String cacheKey = RedisKey.PRESIGNED_URL_CACHE_PREFIX + key;
        redisTemplate.opsForValue().set(cacheKey, presignedUrl, expireSeconds, TimeUnit.SECONDS);
    }
}
