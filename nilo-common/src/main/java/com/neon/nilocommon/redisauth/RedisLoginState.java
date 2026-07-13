package com.neon.nilocommon.redisauth;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userInfo.UserStatus;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;

/**
 * Redis版登录态校验：只读 Redis 中的 token 与账户状态，缺失时不从 MySQL 回写
 * <p>与 web 包 {@code LoginState} 区分；auth 缓存需由 web 登录/自动登录或封禁流程维护</p>
 */
@RequiredArgsConstructor
public class RedisLoginState
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 检查登录状态
     */
    public TokenUserInfo getLoginState(String token)
    {
        // redis中必须有对应记录
        TokenUserInfo tokenUserInfo = getTokenUserInfoByToken(token);
        if (tokenUserInfo == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }

        // 检查账户启用状态
        Long userId = tokenUserInfo.getUserInfo().getUserId();
        if (userId == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }
        assertUserEnabled(userId);
        return tokenUserInfo;
    }

    /**
     * 检查并返回登录用户 ID
     */
    public long getLoginUserId(String token)
    {
        return getLoginState(token).getUserInfo().getUserId();
    }

    /**
     * 校验用户账户是否可用
     */
    public void assertUserEnabled(long userId)
    {
        Integer status = getStatusByUserId(userId);
        if (status == null)
        {
            // auth 未命中：不从MySQL回写，让用户自己靠刷新页面更新缓存
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }
        if (Objects.equals(status, UserStatus.DISABLE.status))
        {
            throw new BusinessException(ResponseCode.BANNED_USER);
        }
    }

    private TokenUserInfo getTokenUserInfoByToken(String token)
    {
        Object result = redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        if (result instanceof TokenUserInfo tokenUserInfo)
        {
            return tokenUserInfo;
        }
        return null;
    }

    private Integer getStatusByUserId(long userId)
    {
        Object result = redisTemplate.opsForValue().get(RedisKey.USER_AUTH_PREFIX + userId);
        if (result instanceof Integer status)
        {
            return status;
        }
        return null;
    }
}
