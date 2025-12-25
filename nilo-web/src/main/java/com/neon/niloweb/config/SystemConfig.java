package com.neon.niloweb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "system")
@Setter
@Getter
@Configuration
public class SystemConfig
{
    /**
     * 单位:MB
     */
    private Long videoSize;
}
