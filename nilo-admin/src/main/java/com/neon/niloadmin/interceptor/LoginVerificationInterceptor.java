package com.neon.niloadmin.interceptor;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.RedisKey;
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
    private static final String FILE_URI = "/file";
    private static final String ARCHIVE_URI = "/archive";

    private final RedisTemplate <String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler)
    {
        // 拦截
        // 不拦静态资源（登录、接口文档的放行在 WebMvcConfig 中按路径配置）
        if (!(handler instanceof HandlerMethod)) return true;

        // 获取
        String token = request.getHeader("token");

        // 包含“/file”或“/archive”时，token不会从请求头传递（HLS请求由播放器发起，通过cookie传递token）
        if (request.getRequestURI().contains(FILE_URI) || request.getRequestURI().contains(ARCHIVE_URI))
        {
            token = ServletUtil.getFromCookie(request, Constants.ADMIN_COOKIE_TOKEN_KEY);
        }

        // 校验
        if (token == null || token.isBlank())
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }

        if (redisTemplate.opsForValue().get(RedisKey.ADMIN_TOKEN_PREFIX + token) == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }

        return true;
    }
}
