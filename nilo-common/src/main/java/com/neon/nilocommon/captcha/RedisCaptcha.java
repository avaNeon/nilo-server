package com.neon.nilocommon.captcha;

import com.neon.nilocommon.entity.constans.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
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

    /**
     * 校验验证码（完全匹配）
     *
     * @param captchaKey key
     * @param code       输入的验证码
     * @return 是否正确
     */
    public boolean verifyCaptchaCode(String captchaKey, String code)
    {
        String trueCode = redisTemplate.opsForValue().get(Constants.REDIS_KEY_CAPTCHA + captchaKey);
        return trueCode != null && trueCode.equals(code);
    }

    /**
     * 删除验证码
     * @return 是否删除成功
     */
    public Boolean deleteCaptcha(String captchaKey)
    {
        return redisTemplate.delete(Constants.REDIS_KEY_CAPTCHA + captchaKey);
    }
}
