package com.neon.niloweb.service;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.config.SystemConfig;
import com.neon.niloweb.enums.UploadQuotaType;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import com.neon.niloweb.repository.redis.UploadQuotaRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@RequiredArgsConstructor
@Service
public class UploadQuotaService
{
    private final SystemConfigRedisRepository systemConfigRedisRepository;

    private final UploadQuotaRedisRepository uploadQuotaRedisRepository;

    /**
     * 预占指定用户当天的上传额度
     */
    public String reserve(long userId, UploadQuotaType type, long uploadSize)
    {
        if (uploadSize < 0)
        {
            throw new BusinessException("上传文件大小非法");
        }

        long limit = getLimitBytes(type);
        String key = buildQuotaKey(userId, type);
        long ttlSeconds = getSecondsUntilTomorrow();
        boolean reserved = uploadQuotaRedisRepository.reserve(key, uploadSize, limit, ttlSeconds);

        if (!reserved)
        {
            throw new BusinessException("今日上传额度不足");
        }

        return key;
    }

    /**
     * 释放已预占的上传额度
     */
    public void release(String key, long uploadSize)
    {
        if (key == null || key.isBlank() || uploadSize <= 0)
        {
            return;
        }

        uploadQuotaRedisRepository.release(key, uploadSize);
    }

    /**
     * 查询指定用户当天剩余上传额度
     */
    public long getRemainingQuota(long userId, UploadQuotaType type)
    {
        long limit = getLimitBytes(type);
        String key = buildQuotaKey(userId, type);

        long usedSize = uploadQuotaRedisRepository.getUsedSize(key);

        return Math.max(0L, limit - usedSize);
    }

    /**
     * 获取指定上传类型的每日额度上限
     */
    private long getLimitBytes(UploadQuotaType type)
    {
        SystemConfig systemConfig = systemConfigRedisRepository.getSystemConfig();
        Integer limitMb = switch (type)
        {
            case IMAGE -> systemConfig.getDailyImageUploadSize();
            case VIDEO -> systemConfig.getDailyVideoUploadSize();
        };

        if (limitMb == null || limitMb < 0)
        {
            throw new BusinessException("上传额度配置非法");
        }

        return (long) limitMb * Constants.Mebibyte;
    }

    /**
     * 构建用户每日上传额度Redis key
     */
    private String buildQuotaKey(long userId, UploadQuotaType type)
    {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern(DatePattern.DATE));

        return RedisKey.UPLOAD_QUOTA_PREFIX + type.getKeyPart() + ":size:" + userId + ":" + date;
    }

    /**
     * 获取当前时间到明日零点的秒数
     */
    private long getSecondsUntilTomorrow()
    {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();

        // 至少有1s
        return Math.max(1, ChronoUnit.SECONDS.between(now, tomorrow));
    }
}
