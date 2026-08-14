package com.neon.nilocommon.repository.redis;

import com.neon.nilocommon.config.SystemConfig;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

@Slf4j
@RequiredArgsConstructor
public class SystemConfigRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 将本地系统配置写入Redis，如果Redis存在配置则什么都不做
     */
    public void addSystemConfigIfAbsent(SystemConfig systemConfig)
    {
        Boolean written = redisTemplate.opsForValue().setIfAbsent(RedisKey.SYSTEM_CONFIG, systemConfig);
        log.info("[系统配置] 启动时 setIfAbsent(nilo:system:config) 结果：{}（true=本次写入了本地默认值，false=Redis里已经有了，没有覆盖）", written);
    }

    /**
     * 从Redis获取系统配置
     */
    public SystemConfig getSystemConfig()
    {
        Object result;
        try
        {
            result = redisTemplate.opsForValue().get(RedisKey.SYSTEM_CONFIG);
        }
        catch (Exception e)
        {
            log.error("[系统配置] 从 Redis 读取 nilo:system:config 时抛出异常", e);
            throw new BusinessException("系统配置不可用");
        }

        if (result instanceof SystemConfig systemConfig)
        {
            return systemConfig;
        }

        log.warn("[系统配置] 读取到的值类型不对，无法转成 SystemConfig：result={}，实际类型={}", result,
                result == null ? "null（key不存在或反序列化后为null）" : result.getClass().getName());
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
