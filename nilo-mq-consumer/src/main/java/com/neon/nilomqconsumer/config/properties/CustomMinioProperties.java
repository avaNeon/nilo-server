package com.neon.nilomqconsumer.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "minio")
public class CustomMinioProperties
{
    private String endpoint;

    private String accessKey;

    private String secretKey;
}
