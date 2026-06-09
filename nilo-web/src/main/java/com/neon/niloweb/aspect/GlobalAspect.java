package com.neon.niloweb.aspect;

import com.neon.niloweb.annotation.Authorized;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.loginState.LoginState;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class GlobalAspect
{
    /* 自动装配 */

    private final LoginState loginState;

    /**
     * 让Spring自动装配当前线程的{@link jakarta.servlet.http.HttpServletRequest}对象
     */
    private final HttpServletRequest request;

    /* 常量 */

    private static final String TOKEN_HEADER = "token";


    /**
     * 被{@link com.neon.niloweb.annotation.Authorized}标注的方法需要在调用前登录校验
     */
    @Before("@annotation(com.neon.niloweb.annotation.Authorized)")
    public void beforeInterceptor(JoinPoint joinPoint)
    {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Authorized authorized = method.getAnnotation(Authorized.class);

        if (authorized != null && authorized.checkLogin())
        {
            checkLogin();
        }
    }

    /**
     * <b>校验用户登陆状态</b><hr/>
     * <p>如果用户没有携带token，或者token记录无法在redis找到，直接拒绝请求</p>
     */
    private void checkLogin()
    {
        String token = request.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasText(token))
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }

        loginState.getLoginState(token);
    }
}
