package com.neon.niloadmin.service;

import com.neon.niloadmin.config.AdminConfig;
import com.neon.nilocommon.entity.constans.Constants;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class AdminService
{
    private final AdminConfig config;

    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 登录
     */
    public String login(String account, String password)
    {
        if (!account.equals(config.getAccount()) || !password.equals(config.getPassword()))
            throw new BusinessException(ResponseCode.LOGIN_FAILURE);
        return generateAndSaveToken(account, 7, TimeUnit.DAYS);
    }

    /**
     * 自动登录
     *
     * @param token
     * @return
     */
    public Boolean autoLogin(String token)
    {
        String account = (String) redisTemplate.opsForValue().get(Constants.REDIS_TOKEN_ADMIN_PREFIX + token);
        if (account == null) return false;
            // 如果过期时间小于1天，则自动延长至7天
        else if (redisTemplate.getExpire(Constants.REDIS_TOKEN_ADMIN_PREFIX + token) < TimeUnit.DAYS.toSeconds(1))
        {
            redisTemplate.opsForValue().set(Constants.REDIS_TOKEN_ADMIN_PREFIX + token, account, 7, TimeUnit.DAYS); // 延长时间至7天
        }
        return true;
    }

    /**
     * 登出
     */
    public Boolean logout(String token)
    {
        return redisTemplate.delete(Constants.REDIS_TOKEN_ADMIN_PREFIX + token);
    }

    /**
     * 生成Token并保存到Redis中
     */
    private String generateAndSaveToken(String account, int time, TimeUnit timeUnit)
    {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(Constants.REDIS_TOKEN_ADMIN_PREFIX + token, account, time, timeUnit);
        return token;
    }
}
