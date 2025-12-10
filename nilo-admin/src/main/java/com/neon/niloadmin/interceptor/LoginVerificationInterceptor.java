package com.neon.niloadmin.interceptor;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.ServletUtil;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登陆验证拦截器
 */
@RequiredArgsConstructor
@Component
public class LoginVerificationInterceptor implements HandlerInterceptor
{
    private static final String ACCOUNT_URI = "/account";
    private static final String FILE_URI = "/file";
    private static final String DOC_URI = "/api-docs";

    private final RedisTemplate <String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler)
    {
        // 拦截
        // 不拦静态资源
        if (!(handler instanceof HandlerMethod)) return true;
        // 不拦登录
        if (request.getRequestURI().contains(ACCOUNT_URI)) return true;
        // 不拦接口文档
        if (request.getRequestURI().contains(DOC_URI)) return true;

        // 获取
        String token = request.getHeader(Constants.COOKIE_TOKEN_ADMIN_KEY);
        // 包含“/file”时，token不会从请求头传递
        if (request.getRequestURI().contains(FILE_URI)) token = ServletUtil.getFromCookie(request, FILE_URI);

        // 校验
        if (token == null || token.isBlank()) throw new BusinessException(ResponseCode.NOT_LOGIN);
        if (redisTemplate.opsForValue().get(Constants.REDIS_TOKEN_ADMIN_PREFIX + token) == null)
            throw new BusinessException(ResponseCode.NOT_LOGIN);

        return true;
    }
}
