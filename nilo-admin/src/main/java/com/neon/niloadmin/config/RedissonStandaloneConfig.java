package com.neon.niloadmin.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "redisson.standalone", havingValue = "true")
public class RedissonStandaloneConfig
{
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "tls.enable", havingValue = "true", matchIfMissing = true)
    public RedissonClient standaloneTlsRedissonClient(RedisProperties redisProperties)
    {
        // 单机 Redis + TLS：直接用域名连接，不存在集群拓扑发现拿到内网裸 IP 的问题
        return Redisson.create(buildConfig(redisProperties, true));
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "tls.enable", havingValue = "false")
    public RedissonClient standalonePlainRedissonClient(RedisProperties redisProperties)
    {
        return Redisson.create(buildConfig(redisProperties, false));
    }

    private static Config buildConfig(RedisProperties redisProperties, boolean tlsEnabled)
    {
        Config config = new Config();
        config.useSingleServer()
              .setAddress(buildAddress(tlsEnabled, redisProperties.getHost() + ":" + redisProperties.getPort()))
              .setPassword(redisProperties.getPassword());
        return config;
    }

    private static String buildAddress(boolean tlsEnabled, String hostPort)
    {
        return (tlsEnabled ? "rediss://" : "redis://") + hostPort;
    }
}
