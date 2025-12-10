package com.neon.niloadmin.service;

import cn.hutool.core.bean.BeanUtil;
import com.neon.niloadmin.config.AdminConfig;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.TokenAdmin;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.Admin;
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
     * 登录<hr/>
     * token在服务器最多保存1天
     */
    public TokenAdmin login(String account, String password)
    {
        for (Admin admin : config.getAdmins())
        {
            if (account.equals(admin.getAccount()) && password.equals(admin.getPassword()))
            {
                TokenAdmin tokenAdmin = BeanUtil.copyProperties(admin, TokenAdmin.class);
                tokenAdmin.setExpireTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
                generateAndSaveToken(tokenAdmin, 1, TimeUnit.DAYS);
                return tokenAdmin;
            }
        }
        // 如果找不到对应的账号密码，则抛出业务异常
        throw new BusinessException(ResponseCode.LOGIN_FAILURE);
    }

    /**
     * 自动登录
     *
     * @param token
     * @return
     */
    public TokenAdmin autoLogin(String token)
    {
        TokenAdmin tokenAdmin = (TokenAdmin) redisTemplate.opsForValue().get(Constants.REDIS_TOKEN_ADMIN_PREFIX + token);
        if (tokenAdmin == null) return null;
            // 如果过期时间小于1天，则自动延长至7天
        else if (tokenAdmin.getExpireTime() - System.currentTimeMillis() < TimeUnit.DAYS.toMillis(1))
        {
            tokenAdmin.setExpireTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7));
            redisTemplate.opsForValue().set(Constants.REDIS_TOKEN_ADMIN_PREFIX + token, tokenAdmin, 7, TimeUnit.DAYS); // 延长时间至7天
        }
        return tokenAdmin;
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
    private void generateAndSaveToken(TokenAdmin admin, int time, TimeUnit timeUnit)
    {
        String token = UUID.randomUUID().toString();
        admin.setToken(token);
        redisTemplate.opsForValue().set(Constants.REDIS_TOKEN_ADMIN_PREFIX + token, admin, time, timeUnit);
    }
}
