package com.neon.nilocommon.loginState;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 登陆状态工具
 */
@RequiredArgsConstructor
public class LoginState
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 检查登录状态
     *
     * @param token 用户登录信息token
     * @return 在Redis保存的TokenUserInfo对象（一定会返回一个非null的值，否则会抛出<b>未登录</b>的异常）
     */
    public TokenUserInfo getLoginState(String token)
    {
        TokenUserInfo tokenUserInfo = (TokenUserInfo) redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        // 如果还没登录，就抛出“未登录”的业务异常
        if (tokenUserInfo == null) throw new BusinessException(ResponseCode.NOT_LOGIN);
        else return tokenUserInfo;
    }

    /**
     * 检查并返回登录用户ID
     *
     * @param token token
     * @return userId
     */
    public long getLoginUserId(String token)
    {
        TokenUserInfo loginState = getLoginState(token);
        Long userId = loginState.getUserInfo().getUserId();
        if (userId == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }
        else
        {
            return userId;
        }
    }
}
