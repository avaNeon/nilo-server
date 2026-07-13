package com.neon.nilocommon.autoconfigure.internalAuth;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 集群内部接口鉴权自动配置<hr/>
 * <ul>
 *     <li>入站：拦截 /inner/**，校验 X-Internal-Token</li>
 *     <li>出站：Feign 自动附带同一 token（有 openfeign 时）</li>
 * </ul>
 */
@Slf4j
@EnableConfigurationProperties(InternalAuthProperties.class)
@ConditionalOnProperty(prefix = "nilo.internal", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfiguration
public class InternalAuthAutoConfiguration
{
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(InternalAuthInterceptor.class)
    public InternalAuthInterceptor internalAuthInterceptor(InternalAuthProperties properties)
    {
        if (!StringUtils.hasText(properties.getToken()))
        {
            log.warn("nilo.internal.token 未配置：/inner/** 请求将被拒绝，请在 Nacos 公共配置中设置");
        }
        return new InternalAuthInterceptor(properties);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(name = "internalAuthWebMvcConfigurer")
    public WebMvcConfigurer internalAuthWebMvcConfigurer(InternalAuthInterceptor internalAuthInterceptor)
    {
        return new WebMvcConfigurer()
        {
            @Override
            public void addInterceptors(InterceptorRegistry registry)
            {
                registry.addInterceptor(internalAuthInterceptor).addPathPatterns("/inner/**");
            }
        };
    }

    @Bean
    @ConditionalOnClass(RequestInterceptor.class)
    @ConditionalOnMissingBean(InternalAuthFeignRequestInterceptor.class)
    public InternalAuthFeignRequestInterceptor internalAuthFeignRequestInterceptor(InternalAuthProperties properties)
    {
        return new InternalAuthFeignRequestInterceptor(properties);
    }
}
