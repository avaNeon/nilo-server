package com.neon.nilocommon.autoconfigure.redisCaptcha;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "common.redis.captcha")
public class RedisCaptchaProperties
{
    public boolean enabled = true;
}
