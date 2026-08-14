package com.neon.nilocommon.autoconfigure.sentinelRateLimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "common.sentinel.rate-limit")
public class SentinelRateLimitProperties
{
    /**
     * 是否开启 Sentinel 限流
     */
    boolean enable = false;
}
