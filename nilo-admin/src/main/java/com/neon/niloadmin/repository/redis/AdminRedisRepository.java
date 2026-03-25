package com.neon.niloadmin.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Repository
public class AdminRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    public TokenAdmin getTokenAdminByToken(String token)
    {
        Object result = redisTemplate.opsForValue().get(RedisKey.ADMIN_TOKEN_PREFIX + token);
        if (result instanceof TokenAdmin tokenAdmin)
        {
            return tokenAdmin;
        }
        return null;
    }

    public void setTokenAdminByToken(String token, TokenAdmin tokenAdmin, int expireDays)
    {
        tokenAdmin.setExpireTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(expireDays));
        redisTemplate.opsForValue().set(RedisKey.ADMIN_TOKEN_PREFIX + token, tokenAdmin, expireDays, TimeUnit.DAYS);
    }

    public Boolean deleteTokenAdminByToken(String token)
    {
        return redisTemplate.delete(RedisKey.ADMIN_TOKEN_PREFIX + token);
    }
}

