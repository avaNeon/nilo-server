package com.neon.niloadmin.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

@Configuration
@ConditionalOnProperty(name = "redisson.standalone", havingValue = "false")
public class RedissonClusterConfig
{
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "tls.enable", havingValue = "true", matchIfMissing = true)
    public RedissonClient clusterTlsRedissonClient(RedisProperties redisProperties)
    {
        return Redisson.create(buildConfig(redisProperties, true));
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "tls.enable", havingValue = "false")
    public RedissonClient clusterPlainRedissonClient(RedisProperties redisProperties)
    {
        return Redisson.create(buildConfig(redisProperties, false));
    }

    private static Config buildConfig(RedisProperties redisProperties, boolean tlsEnabled)
    {
        RedisProperties.Cluster cluster = redisProperties.getCluster();
        if (cluster == null || CollectionUtils.isEmpty(cluster.getNodes()))
        {
            throw new IllegalStateException("redisson.standalone=false requires spring.data.redis.cluster.nodes");
        }

        Config config = new Config();
        var clusterServers = config.useClusterServers().setPassword(redisProperties.getPassword());
        for (String node : cluster.getNodes())
        {
            clusterServers.addNodeAddress(buildAddress(tlsEnabled, node));
        }
        return config;
    }

    private static String buildAddress(boolean tlsEnabled, String hostPort)
    {
        return (tlsEnabled ? "rediss://" : "redis://") + hostPort;
    }
}
