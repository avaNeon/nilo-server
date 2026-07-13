package com.neon.nilocommon.autoconfigure.redisEmailVerification;

import com.neon.nilocommon.email.RedisEmailVerification;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@RequiredArgsConstructor
@EnableConfigurationProperties(RedisEmailVerificationProperties.class)
@ConditionalOnProperty(prefix = "common.redis.email-verification", name = "enabled", havingValue = "true")
@AutoConfiguration
public class RedisEmailVerificationAutoConfiguration
{
    private final RedisTemplate <String, Object> redisTemplate;

    @Bean
    @ConditionalOnMissingBean(RedisEmailVerification.class)
    public RedisEmailVerification redisEmailVerification()
    {
        return new RedisEmailVerification(redisTemplate);
    }
}
