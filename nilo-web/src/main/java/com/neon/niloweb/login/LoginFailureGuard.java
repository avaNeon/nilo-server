package com.neon.niloweb.login;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 登录密码错误次数限制：按「邮箱+IP」计数，10 分钟内失败 5 次则暂时禁止登录。
 */
@Component
@RequiredArgsConstructor
public class LoginFailureGuard
{
    private static final int MAX_FAILURES = 5;

    private static final long FAILURE_TTL_MINUTES = 10;

    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 检查是否可以登录
     *
     * @param email 邮箱
     * @param ip    登录IP
     */
    public void checkAllowed(String email, String ip)
    {
        int result = 0;
        String key = buildKey(email, ip);

        Object value = redisTemplate.opsForValue().get(key);
        if (value != null)
        {
            result = Integer.parseInt(value.toString());
        }

        if (result >= MAX_FAILURES)
        {
            throw new BusinessException(ResponseCode.LOGIN_FAILURE_LOCKED);
        }
    }

    /**
     * 记录失败次数
     *
     * @param email 邮箱
     * @param ip    登录IP
     */
    public void recordFailure(String email, String ip)
    {
        String key = buildKey(email, ip);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, FAILURE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 删除失败次数
     */
    public void clearFailures(String email, String ip)
    {
        redisTemplate.delete(buildKey(email, ip));
    }

    private String buildKey(String email, String ip)
    {
        String normalizedEmail = StringUtils.hasText(email) ? email.trim().toLowerCase() : email;
        return RedisKey.LOGIN_FAILURE_PREFIX + normalizedEmail + ":" + ip;
    }
}
