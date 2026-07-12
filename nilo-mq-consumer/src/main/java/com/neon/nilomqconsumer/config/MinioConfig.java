package com.neon.nilomqconsumer.config;


import com.neon.nilomqconsumer.config.properties.CustomMinioProperties;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CustomMinioProperties.class)
public class MinioConfig
{
    @Bean
    MinioClient minioClient(CustomMinioProperties properties)
    {
        return MinioClient.builder()
                          .endpoint(properties.getEndpoint())
                          .credentials(properties.getAccessKey(), properties.getSecretKey())
                          .build();
    }
}
