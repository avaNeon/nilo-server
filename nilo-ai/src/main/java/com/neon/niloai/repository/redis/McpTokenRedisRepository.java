package com.neon.niloai.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * MCP 专用 token 的存取<hr/>
 * 跟网站登录 token 是两套独立的 key，一个用户同一时间只保留一个有效的 MCP token，
 * 重新生成会把上一个顶掉（靠反向索引 {@link RedisKey#MCP_TOKEN_OWNER_PREFIX} 找到旧 token 再删）
 */
@RequiredArgsConstructor
@Repository
public class McpTokenRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 发一个新 token 给这个用户。如果这个用户已经有一个生效的 token，先把旧的删掉
     */
    public void issue(long userId, String newToken, int expireDays)
    {
        revoke(userId);
        redisTemplate.opsForValue().set(RedisKey.MCP_TOKEN_PREFIX + newToken, userId, expireDays, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(RedisKey.MCP_TOKEN_OWNER_PREFIX + userId, newToken, expireDays, TimeUnit.DAYS);
    }

    /**
     * 通过 token 取出对应的 userId，查不到或者过期返回 null
     */
    public Long getUserId(String token)
    {
        if (!StringUtils.hasText(token))
        {
            return null;
        }
        Object result = redisTemplate.opsForValue().get(RedisKey.MCP_TOKEN_PREFIX + token);
        return result instanceof Number number ? number.longValue() : null;
    }

    /**
     * 撤销这个用户当前生效的 MCP token（如果有）。没有 token 时什么都不做
     */
    public void revoke(long userId)
    {
        String ownerKey = RedisKey.MCP_TOKEN_OWNER_PREFIX + userId;
        Object oldToken = redisTemplate.opsForValue().get(ownerKey);
        if (oldToken instanceof String token)
        {
            redisTemplate.delete(RedisKey.MCP_TOKEN_PREFIX + token);
        }
        redisTemplate.delete(ownerKey);
    }
}
