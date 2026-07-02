package com.neon.nilocommon.repository.redis;

import com.neon.nilocommon.config.SystemConfig;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

@RequiredArgsConstructor
public class SystemConfigRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 将本地系统配置写入Redis，如果Redis存在配置则什么都不做
     */
    public void addSystemConfigIfAbsent(SystemConfig systemConfig)
    {
        redisTemplate.opsForValue().setIfAbsent(RedisKey.SYSTEM_CONFIG, systemConfig);
    }

    /**
     * 从Redis获取系统配置
     */
    public SystemConfig getSystemConfig()
    {
        Object result = redisTemplate.opsForValue().get(RedisKey.SYSTEM_CONFIG);

        if (result instanceof SystemConfig systemConfig)
        {
            return systemConfig;
        }

        throw new BusinessException("系统配置不可用");
    }

    /**
     * 覆盖保存系统配置
     */
    public void saveSystemConfig(SystemConfig systemConfig)
    {
        redisTemplate.opsForValue().set(RedisKey.SYSTEM_CONFIG, systemConfig);
    }

}
