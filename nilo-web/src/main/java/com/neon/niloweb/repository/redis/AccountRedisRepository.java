package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Repository
public class AccountRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 通过token从Redis获得用户记录
     *
     * @param token token
     * @return 用户记录
     */
    public TokenUserInfo getUserInfoByToken(String token)
    {
        Object result = redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        if (result instanceof TokenUserInfo tokenUserInfo)
        {
            return tokenUserInfo;
        }
        else
        {
            return null;
        }
    }

    /**
     * 设置用户信息
     * @param token token
     * @param tokenUserInfo 用户信息
     * @param expireDays 有效时间（单位：天）
     */
    public void setUserInfoByToken(String token, TokenUserInfo tokenUserInfo, int expireDays)
    {
        tokenUserInfo.setExpireTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(expireDays));
        redisTemplate.opsForValue().set(RedisKey.WEB_TOKEN_PREFIX + token, tokenUserInfo, expireDays, TimeUnit.DAYS);
    }

    /**
     * 通过token删除用户在Redis保存的信息
     * @param token token
     * @return 是否删除
     */
    public Boolean deleteTokenUserInfo(String token)
    {
        return redisTemplate.delete(RedisKey.WEB_TOKEN_PREFIX + token);
    }
}
