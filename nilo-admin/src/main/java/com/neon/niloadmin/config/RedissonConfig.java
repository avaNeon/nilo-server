package com.neon.niloadmin.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig
{
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties)
    {
        boolean sslEnabled = redisProperties.getSsl().isEnabled();
        String scheme = sslEnabled ? "rediss://" : "redis://";
        String address = scheme + redisProperties.getHost() + ":" + redisProperties.getPort();

        // 单机 Redis，直接用域名连接，不存在集群拓扑发现拿到内网裸 IP 的问题，
        // 所以也不需要 NAT 映射，也不需要放宽 TLS 证书域名校验
        Config config = new Config();
        config.useSingleServer().setAddress(address).setPassword(redisProperties.getPassword());

        return Redisson.create(config);
    }
}
