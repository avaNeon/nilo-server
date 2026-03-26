package com.neon.nilocommon.autoconfigure.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "common.log.exception")
public class ExceptionLogProperties
{
    public boolean enabled = true;
}
