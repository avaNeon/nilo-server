package com.neon.niloai.service;

import com.neon.niloai.repository.redis.McpQuotaRedisRepository;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 每人每天可以调用 MCP 工具的次数<hr/>
 * 不区分调了哪个工具，只要请求打到 /mcp/message 就算一次，按用户和日期记在 Redis 里，
 * 过期时间设到明天零点，过了零点就是另一条记录
 */
@RequiredArgsConstructor
@Service
public class McpQuotaService
{
    private static final String EXHAUSTED = "今天的 MCP 调用次数已经用完，明天再来";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(DatePattern.DATE);

    private final McpQuotaRedisRepository mcpQuotaRedisRepository;

    /**
     * 每天最多调用次数
     */
    @Value("${quota.mcp-daily-limit}")
    private int dailyLimit;

    /**
     * 扣掉今天的一次。到上限时抛业务异常
     */
    public void consume(long userId)
    {
        String key = RedisKey.MCP_QUOTA_PREFIX + userId + ":" + LocalDate.now().format(DATE);
        long used = mcpQuotaRedisRepository.consume(key, dailyLimit, TimeUtil.getSecondsUntilTomorrow());
        if (used < 0)
        {
            throw new BusinessException(ResponseCode.TOO_MANY_REQUESTS.getCode(), EXHAUSTED);
        }
    }
}
