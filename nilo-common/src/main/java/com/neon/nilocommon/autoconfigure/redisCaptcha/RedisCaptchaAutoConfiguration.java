package com.neon.nilocommon.autoconfigure.redisCaptcha;

import com.neon.nilocommon.captcha.RedisCaptcha;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

@RequiredArgsConstructor
@EnableConfigurationProperties(RedisCaptchaProperties.class)
@ConditionalOnProperty(prefix = "common.redis.captcha", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfiguration
public class RedisCaptchaAutoConfiguration
{
    private final RedisTemplate <String, Object> redisTemplate;

    @Bean
    @ConditionalOnMissingBean(RedisCaptcha.class)
    public RedisCaptcha redisCaptcha()
    {
        return new RedisCaptcha(redisTemplate);
    }
}
