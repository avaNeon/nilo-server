package com.neon.nilocommon.captcha;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis验证码相关操作
 */
@Slf4j
@RequiredArgsConstructor
public class RedisCaptcha
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 将验证码结果保存在redis中
     *
     * @param code 验证码结果
     * @return 唯一标识key
     */
    public String saveCaptchaCode(String code)
    {
        String captchaKey = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(RedisKey.CAPTCHA_PREFIX + captchaKey, code, 3, TimeUnit.MINUTES);
        log.info("[验证码] 写入 key={}（完整 redis key={}）code={}", captchaKey, RedisKey.CAPTCHA_PREFIX + captchaKey, code);
        return captchaKey;
    }

    /**
     * 校验验证码（完全匹配）
     *
     * @param captchaKey key
     * @param code       输入的验证码
     * @return 是否正确
     */
    public boolean verifyCaptchaCode(String captchaKey, String code)
    {
        String redisKey = RedisKey.CAPTCHA_PREFIX + captchaKey;
        Object result;
        try
        {
            result = redisTemplate.opsForValue().get(redisKey);
        }
        catch (Exception e)
        {
            log.error("[验证码] 从 Redis 读取 key={} 时抛出异常，captchaKey={}", redisKey, captchaKey, e);
            throw new BusinessException(ResponseCode.CAPTCHA_FAILED);
        }

        if (result == null)
        {
            log.warn("[验证码] 校验失败：Redis 中找不到 key={}（已过期/已被消费/从未写入成功），提交的 code={}", redisKey, code);
            throw new BusinessException(ResponseCode.CAPTCHA_FAILED);
        }
        String trueCode = result.toString();
        boolean matched = trueCode.equals(code);
        if (!matched)
        {
            log.warn("[验证码] 校验失败：key={} Redis 中存的值={} 与提交的 code={} 不一致", redisKey, trueCode, code);
        }
        return matched;
    }

    /**
     * 删除验证码
     *
     * @return 是否删除成功
     */
    public Boolean deleteCaptcha(String captchaKey)
    {
        return redisTemplate.delete(RedisKey.CAPTCHA_PREFIX + captchaKey);
    }
}
