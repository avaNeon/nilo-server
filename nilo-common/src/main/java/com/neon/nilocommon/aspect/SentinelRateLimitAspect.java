package com.neon.nilocommon.aspect;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.neon.nilocommon.annotation.RateLimit;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

import static com.neon.nilocommon.entity.enums.RateLimitType.USER;

@Slf4j
@Aspect
@RequiredArgsConstructor
public class SentinelRateLimitAspect
{
    private final HttpServletRequest request;

    /**
     * 默认用IP类型的限流
     */
    @Around("@within(com.neon.nilocommon.annotation.RateLimit) || @annotation(com.neon.nilocommon.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable
    {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);
        if (rateLimit == null)
        {
            rateLimit = signature.getMethod().getDeclaringClass().getAnnotation(RateLimit.class);
        }

        // 获取匹配到的接口路径模板
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        // 如果没有匹配到模板，说明出现了意料之外的情况，记录一下
        if (!StringUtils.hasText(pattern))
        {
            pattern = request.getRequestURI();
            log.error("未获取到对应路径：uri={}", pattern);
        }

        // 用类型代表资源，不同类型有不同限流规则
        String resource = rateLimit.by().equals(USER) ? "rate-limit:USER" : "rate-limit:IP";

        // 用户标识作为参数，针对每个用户/IP进行限流
        String limitParam = rateLimit.by().equals(USER) ? request.getHeader("token") : ServletUtil.getClientIp(request);

        try (Entry ignored = SphU.entry(resource, EntryType.IN, 1, limitParam) ;)
        {
            return joinPoint.proceed();
        }
        catch (BlockException e)
        {
            throw new BusinessException(ResponseCode.TOO_MANY_REQUESTS);
        }
    }
}
