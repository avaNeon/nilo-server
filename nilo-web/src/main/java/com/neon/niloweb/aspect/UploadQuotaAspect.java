package com.neon.niloweb.aspect;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.annotation.UploadQuota;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.service.UploadQuotaService;
import com.neon.niloweb.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class UploadQuotaAspect
{
    private static final String TOKEN_HEADER = "token";

    private final HttpServletRequest request;

    private final LoginState loginState;

    private final UploadService uploadService;

    private final UploadQuotaService uploadQuotaService;

    /**
     * 在文件上传接口执行前预占上传额度，非业务异常时释放额度
     */
    @Around("@annotation(com.neon.niloweb.annotation.UploadQuota)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable
    {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        UploadQuota uploadQuota = method.getAnnotation(UploadQuota.class);
        // 获取方法中的 MultipartFile 参数
        MultipartFile file = resolveMultipartFile(joinPoint);

        // 从请求头获取用户ID，一般文件上传功能只开放给登录用户
        long userId = getLoginUserId();
        long fileSize = file.getSize();

        // 消耗用户额度
        String quotaKey = uploadService.reserve(userId, uploadQuota.type(), fileSize, true);

        try
        {
            return joinPoint.proceed();
        }
        catch (BusinessException e)
        {
            throw e;
        }
        catch (Throwable e)
        {
            // 如果是非业务异常，返还额度
            uploadQuotaService.release(quotaKey, fileSize);
            throw e;
        }
    }

    /**
     * 从请求头token解析当前登录用户ID。
     */
    private long getLoginUserId()
    {
        String token = request.getHeader(TOKEN_HEADER);

        if (!StringUtils.hasText(token))
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }

        return loginState.getLoginUserId(token);
    }

    /**
     * 从方法参数中解析第一个MultipartFile参数
     */
    private MultipartFile resolveMultipartFile(ProceedingJoinPoint joinPoint)
    {
        Object[] args = joinPoint.getArgs();

        for (Object arg : args)
        {
            if (arg instanceof MultipartFile multipartFile)
            {
                return multipartFile;
            }
        }

        throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
    }
}
