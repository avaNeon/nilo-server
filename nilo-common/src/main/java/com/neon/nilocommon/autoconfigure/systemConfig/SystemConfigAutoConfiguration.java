package com.neon.nilocommon.autoconfigure.systemConfig;

import com.neon.nilocommon.config.SystemConfig;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@EnableConfigurationProperties(SystemConfig.class)
@AutoConfiguration
public class SystemConfigAutoConfiguration
{
    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    @ConditionalOnMissingBean(SystemConfigRedisRepository.class)
    public SystemConfigRedisRepository systemConfigRedisRepository(RedisTemplate <String, Object> redisTemplate)
    {
        return new SystemConfigRedisRepository(redisTemplate);
    }

    @Bean
    @ConditionalOnBean(SystemConfigRedisRepository.class)
    public ApplicationRunner systemConfigInitializer(SystemConfig systemConfig,
                                                     SystemConfigRedisRepository systemConfigRedisRepository)
    {
        return args -> systemConfigRedisRepository.addSystemConfigIfAbsent(systemConfig);
    }
}
