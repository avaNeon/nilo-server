package com.neon.niloadmin.config;

import com.neon.niloadmin.interceptor.LoginVerificationInterceptor;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录拦截器的放行范围回归测试<hr/>
 * 曾经用 URI 子串 contains("/account") 放行登录接口，导致同在 /account 下的用户管理接口不登录也能调用
 */
class WebMvcConfigTest
{
    /**
     * InterceptorRegistry#getInterceptors 是 protected，子类暴露出来以检查真实配置
     */
    private static class ExposedRegistry extends InterceptorRegistry
    {
        MappedInterceptor loginInterceptor()
        {
            return (MappedInterceptor) getInterceptors().get(0);
        }
    }

    // 未携带 token 时在访问 redis 之前就会抛出，不需要 redisTemplate
    private final LoginVerificationInterceptor interceptor = new LoginVerificationInterceptor(null);

    private MappedInterceptor mappedInterceptor;

    @BeforeEach
    void setUp()
    {
        ExposedRegistry registry = new ExposedRegistry();
        new WebMvcConfig(interceptor).addInterceptors(registry);
        mappedInterceptor = registry.loginInterceptor();
    }

    private boolean intercepted(String uri)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        ServletRequestPathUtils.parseAndCache(request);
        return mappedInterceptor.matches(request);
    }

    /**
     * 不带 token 走一遍拦截器，断言被拒绝为未登录
     */
    private void assertRejectedWithoutToken(String method, String uri) throws NoSuchMethodException
    {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        HandlerMethod handler = new HandlerMethod(this, WebMvcConfigTest.class.getDeclaredMethod("setUp"));

        BusinessException e = assertThrows(BusinessException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler));
        assertEquals(ResponseCode.NOT_LOGIN.getCode(), e.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/captcha", "/admin/login", "/admin/autoLogin", "/admin/logout", "/v3/api-docs", "/v3/api-docs/swagger-config", "/v3/api-docs/default"})
    void publicPathsSkipLogin(String uri)
    {
        assertFalse(intercepted(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 用户管理
            "/account/list", "/account/count", "/account/status",
            // 路径中恰好含有放行片段的其它接口（路径变量可以被任意填写）
            "/video/admin", "/video/api-docs", "/archive/file/admin", "/video/video/admin/login",
            // AdminController 以后新增的接口不会被自动放行
            "/admin/password"
    })
    void otherPathsRequireLogin(String uri) throws NoSuchMethodException
    {
        // 既要被拦截器覆盖，拦截器内部也不能再按路径放行
        assertTrue(intercepted(uri));
        assertRejectedWithoutToken("GET", uri);
    }

    @Test
    void userManagementWithoutTokenIsRejected() throws NoSuchMethodException
    {
        assertRejectedWithoutToken("POST", "/account/list");
    }
}
