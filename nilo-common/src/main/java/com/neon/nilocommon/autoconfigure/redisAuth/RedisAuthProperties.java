package com.neon.nilocommon.autoconfigure.redisAuth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "common.redis-auth")
public class RedisAuthProperties
{
    /**
     * 是否启用 Redis 侧登录校验自动装配。
     */
    private boolean enabled = false;
}
