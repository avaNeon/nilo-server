package com.neon.niloadmin.service;

import cn.hutool.core.bean.BeanUtil;
import com.neon.niloadmin.config.AdminConfig;
import com.neon.niloadmin.repository.redis.AdminRedisRepository;
import com.neon.nilocommon.entity.dto.TokenAdmin;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.Admin;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class AdminService
{
    private final AdminConfig config;

    private final AdminRedisRepository adminRedisRepository;

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
                generateAndSaveToken(tokenAdmin, 1);
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
        TokenAdmin tokenAdmin = adminRedisRepository.getTokenAdminByToken(token);
        if (tokenAdmin == null) return null;
            // 如果过期时间小于1天，则自动延长至7天
        else if (tokenAdmin.getExpireTime() - System.currentTimeMillis() < TimeUnit.DAYS.toMillis(1))
        {
            adminRedisRepository.setTokenAdminByToken(token, tokenAdmin, 7); // 延长时间至7天
        }
        return tokenAdmin;
    }

    /**
     * 登出
     */
    public Boolean logout(String token)
    {
        return adminRedisRepository.deleteTokenAdminByToken(token);
    }

    /**
     * 生成Token并保存到Redis中
     */
    private void generateAndSaveToken(TokenAdmin admin, int expireDays)
    {
        String token = UUID.randomUUID().toString();
        admin.setToken(token);
        adminRedisRepository.setTokenAdminByToken(token, admin, expireDays);
    }
}
