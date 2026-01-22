package com.neon.nilocommon.autoconfigure.serialization;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "common.global.serialization")
public class GlobalSerializationProperties
{
    public String pattern = "yyyy-MM-dd HH:mm:ss";

    public boolean enabled = true;
}
