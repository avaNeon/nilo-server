package com.neon.niloadmin.config;

import com.neon.niloadmin.interceptor.LoginVerificationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器配置器
 */
@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer
{
    private final LoginVerificationInterceptor loginVerificationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        // 让登陆验证拦截器拦截所有请求
        registry.addInterceptor(loginVerificationInterceptor).addPathPatterns("/**");
    }
}
