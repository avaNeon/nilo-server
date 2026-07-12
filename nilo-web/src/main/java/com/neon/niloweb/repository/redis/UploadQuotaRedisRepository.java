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

    /**
     * 严格模式下预占上传额度的 Lua 脚本<hr/>
     * 先检查后自增，一旦会超额就直接拒绝，不产生任何副作用（不会写入/修改key）<br/>
     * 如果传入参数不合法，返回0<br/>
     * 如果记录额度成功，返回1<br/>
     * 如果会导致超额，拒绝本次记录，返回2<br/>
     */
    private static final DefaultRedisScript <Long> RESERVE_STRICT_SCRIPT = new DefaultRedisScript <>("""
            local key = KEYS[1]
            local increment = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            
            if increment == nil or limit == nil or ttl == nil or increment < 0 then
                return 0
            end
            
            local current = tonumber(redis.call('GET', key) or '0')
            if current + increment > limit then
                return 2
            end
            
            local updated = redis.call('INCRBY', key, increment)
            if updated == increment or redis.call('TTL', key) < 0 then
                redis.call('EXPIRE', key, ttl)
            end
            
            return 1
            """, Long.class);

    /**
     * 非严格模式下预占上传额度的 Lua 脚本<hr/>
     * 无论是否超额都无条件自增，用于补记已经产生的真实用量（不能拒绝已发生的事实）<br/>
     * 如果传入参数不合法，返回0<br/>
     * 如果记录额度成功，返回1<br/>
     * 如果记录额度后，发现超额，返回2（但依然记录成功）<br/>
     */
    private static final DefaultRedisScript <Long> RESERVE_LENIENT_SCRIPT = new DefaultRedisScript <>("""
            local key = KEYS[1]
            local increment = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            
            if increment == nil or limit == nil or ttl == nil or increment < 0 then
                return 0
            end
            
            local updated = redis.call('INCRBY', key, increment)
            if updated == increment or redis.call('TTL', key) < 0 then
                redis.call('EXPIRE', key, ttl)
            end
            
            if updated > limit then
                return 2
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
     * 原子预占上传额度<hr/>
     *
     * @param strictMode 严格模式下，一旦会超额则直接拒绝且不产生任何副作用；非严格模式下，无论是否超额都无条件记录
     * @return 0: 传入参数不合法<br/>
     *         1: 预占额度成功<br/>
     *         2: 严格模式下拒绝本次记录（未写入）；非严格模式下超额但依然记录成功
     */
    public long reserve(String key, long increment, long limit, long ttlSeconds, boolean strictMode)
    {
        DefaultRedisScript <Long> script = strictMode ? RESERVE_STRICT_SCRIPT : RESERVE_LENIENT_SCRIPT;

        return stringRedisTemplate.execute(script,
                                           List.of(key),
                                           String.valueOf(increment),
                                           String.valueOf(limit),
                                           String.valueOf(ttlSeconds));
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
