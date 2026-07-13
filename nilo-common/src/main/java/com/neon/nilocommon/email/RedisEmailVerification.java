package com.neon.nilocommon.email;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.EmailScene;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Redis邮箱验证码相关操作<hr/>
 * <p>验证码本身不落库，全部基于Redis实现，包含：</p>
 * <ul>
 *     <li>60s发送冷却，防止连续重复发送</li>
 *     <li>5小时内最多5次发送请求（固定窗口），防止邮箱被刷</li>
 *     <li>验证码最多可尝试5次，超过直接失效，防止暴力破解</li>
 * </ul>
 */
@RequiredArgsConstructor
public class RedisEmailVerification
{
    private final RedisTemplate <String, Object> redisTemplate;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 验证码有效期（单位：秒）
     */
    private static final long CODE_EXPIRE_SECONDS = 5 * 60;

    /**
     * 尝试次数计数器有效期（比验证码略长，避免验证码还有效但计数器先消失）
     */
    private static final long ATTEMPTS_EXPIRE_SECONDS = CODE_EXPIRE_SECONDS + 3;

    /**
     * 验证码最多可尝试次数
     */
    private static final int MAX_ATTEMPTS = 5;

    /**
     * 发送冷却时间（单位：秒）
     */
    private static final long COOLDOWN_SECONDS = 60;

    /**
     * 发送频次限制窗口（单位：小时）
     */
    private static final long FREQ_WINDOW_HOURS = 5;

    /**
     * 发送频次限制窗口内最多允许的发送次数
     */
    private static final long MAX_FREQ_COUNT = 5;

    /**
     * 字符集：数字 + 大写字母（排除易混淆的 0/O、1/I）
     */
    private static final char[] CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    /**
     * 生成并保存邮箱验证码<hr/>
     * <p>内部会先做冷却和频次限制校验，超限会抛出{@link BusinessException}</p>
     *
     * @param scene 场景
     * @param email 邮箱
     * @return 生成的6位数字字母验证码
     */
    public String generateAndSaveCode(EmailScene scene, String email)
    {
        /* --- 校验部分 --- */

        /* --- 构建发送冷却key --- */

        String cooldownKey = buildKey(RedisKey.EMAIL_CODE_COOLDOWN_PREFIX, scene, email);

        // 在redis中创建该key，TTL=1min
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(cooldownKey, 1, COOLDOWN_SECONDS, TimeUnit.SECONDS);
        // 如果创建失败说明已经存在该key，此时处于冷却时间，返回等待冷却时间提示
        if (!Boolean.TRUE.equals(acquired))
        {
            throw new BusinessException(ResponseCode.EMAIL_CODE_SEND_TOO_FREQUENT);
        }

        /* --- 构建发送频次key --- */

        String freqKey = buildKey(RedisKey.EMAIL_CODE_FREQ_PREFIX, scene, email);

        // 当前email的验证码请求频次+1
        Long freqCount = redisTemplate.opsForValue().increment(freqKey);

        // 如果第一次创建该key，那么给与5h的过期窗口
        if (freqCount != null && freqCount == 1)
        {
            redisTemplate.expire(freqKey, FREQ_WINDOW_HOURS, TimeUnit.HOURS);
        }

        // 如果发现请求频次超过限制，返回请求次数过多
        if (freqCount != null && freqCount > MAX_FREQ_COUNT)
        {
            throw new BusinessException(ResponseCode.EMAIL_CODE_SEND_LIMIT_EXCEEDED);
        }

        // 生成验证码
        String code = generateCode();

        // 将验证码保存在redis中，TTL=5min
        redisTemplate.opsForValue()
                     .set(buildKey(RedisKey.EMAIL_CODE_PREFIX, scene, email), code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 同时加上一个尝试次数计数器，TTL=5min+3s
        redisTemplate.opsForValue()
                     .set(buildKey(RedisKey.EMAIL_CODE_ATTEMPTS_PREFIX, scene, email),
                          MAX_ATTEMPTS,
                          ATTEMPTS_EXPIRE_SECONDS,
                          TimeUnit.SECONDS);
        return code;
    }

    /**
     * 校验邮箱验证码<hr/>
     * <p>校验通过会删除验证码和尝试次数计数器；校验失败会消耗一次尝试次数，
     * 次数耗尽后验证码直接失效（用户需要重新申请，从而受到发送冷却/频次限制的约束）</p>
     *
     * @param scene 场景
     * @param email 邮箱
     * @param code  用户提交的验证码
     */
    public void verifyCode(EmailScene scene, String email, String code)
    {
        // 把保存的验证码和尝试计数器都拿出来

        String codeKey = buildKey(RedisKey.EMAIL_CODE_PREFIX, scene, email);
        String attemptsKey = buildKey(RedisKey.EMAIL_CODE_ATTEMPTS_PREFIX, scene, email);

        Object saved = redisTemplate.opsForValue().get(codeKey);
        if (saved == null)
        {
            throw new BusinessException(ResponseCode.EMAIL_CODE_INVALID.getCode(), "验证码已失效，请重新获取");
        }

        // 成功匹配
        // 忽略大小写，避免用户输入时大小写不一致导致误判
        if (saved.toString().equalsIgnoreCase(code))
        {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptsKey);
            return;
        }

        // 错误匹配
        Long remaining = redisTemplate.opsForValue().decrement(attemptsKey);
        // 如果尝试次数过多，删除这个验证码
        if (remaining == null || remaining <= 0)
        {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptsKey);
            throw new BusinessException(ResponseCode.EMAIL_CODE_INVALID.getCode(), "验证码错误次数过多，请重新获取验证码");
        }
        throw new BusinessException(ResponseCode.EMAIL_CODE_INVALID.getCode(), "验证码错误，还可尝试" + remaining + "次");
    }

    private String generateCode()
    {
        char[] code = new char[6];
        for (int i = 0 ; i < code.length ; i++)
        {
            code[i] = CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)];
        }
        return new String(code);
    }

    private String buildKey(String prefix, EmailScene scene, String email)
    {
        return prefix + scene.name() + ":" + email;
    }
}
