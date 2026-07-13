package com.neon.nilocommon.autoconfigure.internalAuth;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 校验 /inner/** 请求是否携带正确的内部 token
 */
@RequiredArgsConstructor
public class InternalAuthInterceptor implements HandlerInterceptor
{
    private final InternalAuthProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler)
    {
        String expected = properties.getToken();
        if (!StringUtils.hasText(expected))
        {
            throw new BusinessException(ResponseCode.NO_PERMISSION.getCode(), "内部鉴权密钥未配置");
        }

        String actual = request.getHeader(Constants.INTERNAL_TOKEN_HEADER);
        if (!StringUtils.hasText(actual) || !constantTimeEquals(expected, actual))
        {
            throw new BusinessException(ResponseCode.NO_PERMISSION);
        }
        return true;
    }

    private boolean constantTimeEquals(String expected, String actual)
    {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
