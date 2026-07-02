package com.neon.niloweb.repository.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@RequiredArgsConstructor
@Repository
public class UploadQuotaRedisRepository
{
    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript <Long> RESERVE_SCRIPT = new DefaultRedisScript <>("""
            local key = KEYS[1]
            local increment = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            
            if increment == nil or limit == nil or ttl == nil or increment < 0 then
                return 0
            end
            
            local current = tonumber(redis.call('GET', key) or '0')
            if current + increment > limit then
                return 0
            end
            
            local updated = redis.call('INCRBY', key, increment)
            if updated == increment or redis.call('TTL', key) < 0 then
                redis.call('EXPIRE', key, ttl)
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript <Long> RELEASE_SCRIPT = new DefaultRedisScript <>("""
            local key = KEYS[1]
            local decrement = tonumber(ARGV[1])
            
            if decrement == nil or decrement <= 0 then
                return 0
            end
            
            local current = tonumber(redis.call('GET', key) or '0')
            if current <= decrement then
                redis.call('DEL', key)
                return 1
            end
            
            redis.call('DECRBY', key, decrement)
            return 1
            """, Long.class);

    /**
     * 原子预占上传额度
     */
    public boolean reserve(String key, long increment, long limit, long ttlSeconds)
    {
        Long result = stringRedisTemplate.execute(RESERVE_SCRIPT,
                                                  List.of(key),
                                                  String.valueOf(increment),
                                                  String.valueOf(limit),
                                                  String.valueOf(ttlSeconds));
        return result == 1L;
    }

    /**
     * 释放上传额度
     */
    public void release(String key, long decrement)
    {
        stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(key), String.valueOf(decrement));
    }

    /**
     * 查询使用的上传额度
     * @param key redis key
     * @return 额度，单位为byte
     */
    public long getUsedSize(String key)
    {
        String value = stringRedisTemplate.opsForValue().get(key);

        if (value == null || value.isBlank())
        {
            return 0L;
        }

        return Long.parseLong(value);
    }
}
