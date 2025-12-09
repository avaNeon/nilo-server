package com.neon.niloadmin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@ConfigurationProperties(prefix = "admin")
@Configuration
public class AdminConfig
{
    private String account;
    private String password;
}
