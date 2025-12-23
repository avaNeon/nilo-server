package com.neon.niloweb.config;

import cn.hutool.core.lang.Snowflake;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake algorithm 配置类<hr/>
 * workerId和datacenterId都是手动配置，因为规模不大，后续可以使用nacos方便地修改<hr/>
 * 如果集群过大，可以使用redis自动生成这两个id
 */
@Slf4j
@Setter
@Getter
@ConfigurationProperties(prefix = "snowflake")
@Configuration
public class SnowflakeConfig
{
    private Short datacenterId;

    private Short workerId;

    @Bean
    public Snowflake snowflake()
    {
        if (datacenterId == null || datacenterId < 0 || datacenterId > 31) throw new RuntimeException("datacenterId is illegal");
        if (workerId == null || workerId < 0 || workerId > 31) throw new RuntimeException("workerId is illegal");
        log.info("datacenterId:{},workerId:{}", datacenterId, workerId);
        return new Snowflake(workerId, datacenterId);
    }
}
