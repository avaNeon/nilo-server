package com.neon.nilocommon.autoconfigure.serialization;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "common.global.serialization")
public class GlobalSerializationProperties
{
    /**
     * 是否启用此序列化规则
     */
    public boolean enabled = true;

    /**
     * 默认的DATETIME类型格式
     */
    public String pattern = "yyyy-MM-dd HH:mm:ss";

    /**
     * JSON转化是否要求必须有全部属性
     */
    public boolean jsonFailOnUnknownProperties = false;
}
