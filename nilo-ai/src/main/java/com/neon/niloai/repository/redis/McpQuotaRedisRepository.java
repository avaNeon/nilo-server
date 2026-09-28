package com.neon.niloai.repository.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 每日 MCP 调用次数<hr/>
 * 次数是纯数字，用字符串读写，避免和登录态那套 Jackson 序列化搅在一起
 */
@RequiredArgsConstructor
@Repository
public class McpQuotaRedisRepository
{
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 还有余量就加一，并在第一次写入时设过期时间。已经到上限则什么都不改
     * <p>返回加一后的次数；到上限返回 -1</p>
     */
    private static final DefaultRedisScript <Long> CONSUME_SCRIPT = new DefaultRedisScript <>("""
                                                                                                      local key = KEYS[1]
                                                                                                      local limit = tonumber(ARGV[1])
                                                                                                      local ttl = tonumber(ARGV[2])
                                                                                                      
                                                                                                      if limit == nil or ttl == nil or limit < 1 or ttl < 1 then
                                                                                                          return -1
                                                                                                      end
                                                                                                      
                                                                                                      local current = tonumber(redis.call('GET', key) or '0')
                                                                                                      if current >= limit then
                                                                                                          return -1
                                                                                                      end
                                                                                                      
                                                                                                      local updated = redis.call('INCR', key)
                                                                                                      if updated == 1 or redis.call('TTL', key) < 0 then
                                                                                                          redis.call('EXPIRE', key, ttl)
                                                                                                      end
                                                                                                      
                                                                                                      return updated
                                                                                                      """, Long.class);

    /**
     * @return 扣减后的已用次数；已经到上限时返回 -1，key 保持原样
     */
    public long consume(String key, int limit, long ttlSeconds)
    {
        return stringRedisTemplate.execute(CONSUME_SCRIPT, List.of(key), String.valueOf(limit), String.valueOf(ttlSeconds));
    }
}
