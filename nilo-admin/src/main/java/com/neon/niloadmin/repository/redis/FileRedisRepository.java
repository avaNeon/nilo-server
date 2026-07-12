package com.neon.niloadmin.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Repository
public class FileRedisRepository
{
    // 该缓存的value是纯字符串（预签名URL），且与nilo-web共用同一个key，
    // 必须使用String序列化方式存取，不能用注入了GenericJackson2JsonRedisSerializer的<String, Object>模板，
    // 否则value会被序列化为带引号的JSON字符串，被nilo-web用StringRedisSerializer读取时引号会成为URL的一部分
    private final RedisTemplate <String, String> redisTemplate;

    public String getPresignedUrlCache(String key)
    {
        String cacheKey = RedisKey.PRESIGNED_URL_CACHE_PREFIX + key;
        return redisTemplate.opsForValue().get(cacheKey);
    }

    public void setPresignedUrlCache(String key, String presignedUrl, int expireSeconds)
    {
        String cacheKey = RedisKey.PRESIGNED_URL_CACHE_PREFIX + key;
        redisTemplate.opsForValue().set(cacheKey, presignedUrl, expireSeconds, TimeUnit.SECONDS);
    }
}
