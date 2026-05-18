package com.neon.nilocommon.autoconfigure.loginState;

import com.neon.nilocommon.loginState.LoginState;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@EnableConfigurationProperties(LoginStateProperties.class)
@ConditionalOnProperty(prefix = "common.login-state", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@AutoConfiguration
public class LoginStateAutoConfiguration
{
    private final RedisTemplate <String, Object> redisTemplate;

    @Bean
    @ConditionalOnMissingBean(LoginState.class)
    public LoginState loginState()
    {
        return new LoginState(redisTemplate);
    }
}
