package com.neon.niloweb.service;

import com.neon.nilocommon.config.SystemConfig;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.po.MediaOwnership;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import com.neon.niloweb.enums.UploadQuotaType;
import com.neon.niloweb.mapper.MediaOwnershipMapper;
import com.neon.niloweb.repository.redis.UploadQuotaRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class UploadQuotaService
{
    private final SystemConfigRedisRepository systemConfigRedisRepository;

    private final UploadQuotaRedisRepository uploadQuotaRedisRepository;

    private final MediaOwnershipMapper <MediaOwnership, MediaOwnership> mediaOwnershipMapper;

    private final UploadService uploadService;

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
     * 释放指定用户在某天的上传额度
     */
    public void releaseForDate(long userId, UploadQuotaType type, long size, LocalDate date)
    {
        if (size <= 0)
        {
            return;
        }

        String dateName = date.format(DateTimeFormatter.ofPattern(DatePattern.DATE));
        String key = RedisKey.UPLOAD_QUOTA_PREFIX + String.join(":", type.getKeyPart(), "size", userId + "", dateName);

        release(key, size);
    }

    /**
     * 查询指定用户当天剩余上传额度
     */
    public long getRemainingQuota(long userId, UploadQuotaType type)
    {
        LocalDate now = LocalDate.now();
        return getRemainingQuota(userId, type, now);
    }

    /**
     * 查询指定用户指定日期的剩余上传额度
     */
    public long getRemainingQuota(long userId, UploadQuotaType type, LocalDate date)
    {
        long limit = getLimitBytes(type);
        String date1 = date.format(DateTimeFormatter.ofPattern(DatePattern.DATE));

        String key = RedisKey.UPLOAD_QUOTA_PREFIX + type.getKeyPart() + ":size:" + userId + ":" + date1;

        // 如果是视频
        if (Objects.equals(type, UploadQuotaType.VIDEO))
        {
            // 查询最后一个video的记录
            MediaOwnership lastOwnership = mediaOwnershipMapper.selectLastByOwnerIdAndBucket(userId,
                                                                                             MinioBucket.MINIO_VIDEO_BUCKET);
            // 如果没有记录，说明还没传过视频不用管
            // 如果有记录，看看有没有用过，如果用过了，说明上一次上传额度没记录，调用下请求key的方法刷新下记录
            if (lastOwnership != null && lastOwnership.getUsed().equals(1))
            {
                uploadService.getUploadKey(LocalDateTime.now(), userId);
            }
        }

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

}
