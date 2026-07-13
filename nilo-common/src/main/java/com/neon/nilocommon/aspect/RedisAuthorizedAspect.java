package com.neon.nilocommon.aspect;

import com.neon.nilocommon.annotation.RedisAuthorized;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.redisauth.RedisLoginState;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 处理 {@link RedisAuthorized}：请求前校验 token / 账户状态（仅 Redis）。
 */
@Aspect
@RequiredArgsConstructor
public class RedisAuthorizedAspect
{
    private final RedisLoginState redisLoginState;

    private final HttpServletRequest request;

    private static final String TOKEN_HEADER = "token";

    @Before("@annotation(com.neon.nilocommon.annotation.RedisAuthorized)")
    public void beforeInterceptor(JoinPoint joinPoint)
    {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RedisAuthorized redisAuthorized = method.getAnnotation(RedisAuthorized.class);

        if (redisAuthorized != null && redisAuthorized.checkLogin())
        {
            checkLogin();
        }
    }

    private void checkLogin()
    {
        String token = request.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasText(token))
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }
        redisLoginState.getLoginState(token);
    }
}
