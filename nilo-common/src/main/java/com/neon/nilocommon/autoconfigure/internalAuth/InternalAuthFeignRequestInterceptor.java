package com.neon.nilocommon.autoconfigure.internalAuth;

import com.neon.nilocommon.entity.constants.Constants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 为所有 Feign 出站请求自动附加内部 token
 */
@RequiredArgsConstructor
public class InternalAuthFeignRequestInterceptor implements RequestInterceptor
{
    private final InternalAuthProperties properties;

    @Override
    public void apply(RequestTemplate template)
    {
        String token = properties.getToken();
        if (StringUtils.hasText(token))
        {
            template.header(Constants.INTERNAL_TOKEN_HEADER, token);
        }
    }
}
