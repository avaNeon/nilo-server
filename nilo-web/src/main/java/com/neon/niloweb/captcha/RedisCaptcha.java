package com.neon.niloweb.captcha;

import com.neon.nilocommon.entity.constans.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class RedisCaptcha
{
    private final RedisTemplate <String, String> redisTemplate;

    /**
     * 将验证码结果保存在redis中
     *
     * @param code 验证码结果
     * @return 唯一标识key
     */
    public String saveCaptchaCode(String code)
    {
        String captchaKey = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(Constants.REDIS_KEY_CAPTCHA + captchaKey, code, 3, TimeUnit.MINUTES);
        return captchaKey;
    }
}
