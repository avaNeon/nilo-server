package com.neon.nilocommon.autoconfigure.redisAuth;

import com.neon.nilocommon.aspect.RedisAuthorizedAspect;
import com.neon.nilocommon.redisauth.RedisLoginState;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

@ConditionalOnClass({RedisTemplate.class, Aspect.class})
@EnableConfigurationProperties(RedisAuthProperties.class)
@ConditionalOnProperty(prefix = "common.redis-auth", name = "enabled", havingValue = "true")
@AutoConfiguration
public class RedisAuthAutoConfiguration
{
    @Bean
    @ConditionalOnMissingBean(RedisLoginState.class)
    public RedisLoginState redisLoginState(RedisTemplate <String, Object> redisTemplate)
    {
        return new RedisLoginState(redisTemplate);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnMissingBean(RedisAuthorizedAspect.class)
    public RedisAuthorizedAspect redisAuthorizedAspect(RedisLoginState redisLoginState, HttpServletRequest request)
    {
        return new RedisAuthorizedAspect(redisLoginState, request);
    }
}
