package com.neon.nilocommon.autoconfigure.sentinelRateLimit;

import com.neon.nilocommon.aspect.SentinelRateLimitAspect;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@ConditionalOnProperty(prefix = "common.sentinel.rate-limit", name = "enable", havingValue = "true")
@EnableConfigurationProperties(SentinelRateLimitProperties.class)
@AutoConfiguration
public class SentinelRateLimitAutoConfiguration
{
    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    public SentinelRateLimitAspect sentinelRateLimitAspect(HttpServletRequest request)
    {
        return new SentinelRateLimitAspect(request);
    }
}
