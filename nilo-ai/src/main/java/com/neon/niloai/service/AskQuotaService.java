package com.neon.niloai.service;

import com.neon.niloai.entity.vo.AskQuotaVO;
import com.neon.niloai.repository.redis.AskQuotaRedisRepository;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 每人每天可以提问的次数<hr/>
 * 次数按用户和日期记在 Redis 里，过期时间设到明天零点。日期也写进 key，过了零点就是另一条记录
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AskQuotaService
{
    private static final String EXHAUSTED = "今天的提问次数已经用完，明天再来";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(DatePattern.DATE);

    private final AskQuotaRedisRepository askQuotaRedisRepository;

    /**
     * 每天最多提问次数
     */
    @Value("${quota.ask-daily-limit}")
    private int dailyLimit;

    /**
     * 扣掉今天的一次。到上限时抛业务异常，key 不会被改
     *
     * @return 这次扣减用的 Redis key，提问失败时拿它退还
     */
    public String consume(long userId)
    {
        String key = key(userId, LocalDate.now());
        long used = askQuotaRedisRepository.consume(key, dailyLimit, TimeUtil.getSecondsUntilTomorrow());
        if (used < 0)
        {
            throw new BusinessException(ResponseCode.TOO_MANY_REQUESTS.getCode(), EXHAUSTED);
        }
        return key;
    }

    /**
     * 模型或查视频失败时把这一次还回去。还失败只记日志，不盖住原来的错误
     */
    public void refund(String key)
    {
        try
        {
            askQuotaRedisRepository.refund(key);
        }
        catch (RuntimeException e)
        {
            log.warn("归还提问额度失败, key={}", key, e);
        }
    }

    public AskQuotaVO usage(long userId)
    {
        int used = askQuotaRedisRepository.used(key(userId, LocalDate.now()));
        int shown = Math.min(used, dailyLimit);
        return new AskQuotaVO(shown, dailyLimit);
    }

    private static String key(long userId, LocalDate date)
    {
        return RedisKey.AI_ASK_QUOTA_PREFIX + userId + ":" + date.format(DATE);
    }
}
