package com.neon.niloweb.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig
{
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient()
    {
        Config config = new Config();
        config.useClusterServers()
              .addNodeAddress("redis://192.168.6.10:6379",
                              "redis://192.168.6.10:6380",
                              "redis://192.168.6.10:6381",
                              "redis://192.168.6.10:6382",
                              "redis://192.168.6.10:6383",
                              "redis://192.168.6.10:6384")
              .setPassword("redis")
              .setScanInterval(2000);
        return Redisson.create(config);
    }
}
