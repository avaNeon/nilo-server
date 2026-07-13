package com.neon.nilocommon.autoconfigure.redisEmailVerification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "common.redis.email-verification")
public class RedisEmailVerificationProperties
{
    public boolean enabled = true;
}
