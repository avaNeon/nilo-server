package com.neon.nilocommon.autoconfigure.redisCaptcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "common.redis.captcha")
public class RedisCaptchaProperties
{
    public boolean enabled = true;
}
