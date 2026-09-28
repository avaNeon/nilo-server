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

    /**
     * 不需要登录的接口，按完整路径精确放行<br/>
     * 不要改回 URI 子串匹配：子串会误放行路径里恰好含该片段的其它接口（原先 contains("/account") 放行了 /account 下的用户管理接口）
     */
    static final String[] PUBLIC_PATHS = {"/admin/captcha",
                                          "/admin/login",
                                          "/admin/autoLogin",
                                          "/admin/logout",
                                          "/v3/api-docs/**"};

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        // 让登陆验证拦截器拦截除登录、接口文档外的所有请求
        registry.addInterceptor(loginVerificationInterceptor).addPathPatterns("/**").excludePathPatterns(PUBLIC_PATHS);
    }
}
