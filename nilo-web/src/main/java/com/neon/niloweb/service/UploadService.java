package com.neon.niloweb.service;

import com.neon.nilocommon.config.SystemConfig;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.MediaOwnership;
import com.neon.nilocommon.entity.po.UserUploadVideoLock;
import com.neon.nilocommon.entity.query.MediaOwnershipQuery;
import com.neon.nilocommon.entity.query.UserUploadVideoLockQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import com.neon.nilocommon.util.TimeUtil;
import com.neon.niloweb.enums.UploadQuotaType;
import com.neon.niloweb.feign.storage.VideoFileFeignClient;
import com.neon.niloweb.mapper.MediaOwnershipMapper;
import com.neon.niloweb.mapper.UserUploadVideoLockMapper;
import com.neon.niloweb.repository.redis.UploadQuotaRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 这是 {@link com.neon.niloweb.service.FileService} 的衍生类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UploadService
{
    private final SystemConfigRedisRepository systemConfigRedisRepository;

    private final UploadQuotaRedisRepository uploadQuotaRedisRepository;

    private final MediaOwnershipMapper <MediaOwnership, MediaOwnershipQuery> mediaOwnershipMapper;

    private final UserUploadVideoLockMapper <UserUploadVideoLock, UserUploadVideoLockQuery> userUploadVideoLockMapper;

    private final VideoFileFeignClient videoFileFeignClient;

    /**
     * 获取上传文件的key<hr/>
     * <p>自 {@link FileService#uploadVideo(long, long)} 衍生出的方法</p>
     * 会新开一个事务，并对用户的上传视频文件行为加锁
     *
     * @param now    记录开始时间
     * @param userId 用户ID
     * @return 上传文件的key
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public String getUploadKey(LocalDateTime now, long userId)
    {
        // 先把需要的变量准备好
        // 本轮返回key
        String key = null;
        // 最后一个video的空间占用
        long lastFileSize = 0;
        // 最后一个video的请求时间
        LocalDateTime lastCreatedTime = null;

        // 查看目前本session的锁等待时间
        Integer original_innodb_lock_wait_timeout = userUploadVideoLockMapper.selectInnodbLockWaitTimeout();

        try
        {
            // 先缩短本session的锁等待时间，修改失败会自动抛出异常
            userUploadVideoLockMapper.setSessionInnodbLockWaitTimeout(5);

            // 先加X锁，X锁会随着事务的提交/回滚而自动释放
            Integer count = userUploadVideoLockMapper.tryLock(userId);

            // 如果抢锁失败
            if (count.equals(0))
            {
                throw new RuntimeException("请稍后再试");
            }

            // 查询最后一个video的记录
            MediaOwnership lastOwnership = mediaOwnershipMapper.selectLastByOwnerIdAndBucket(userId,
                                                                                             MinioBucket.MINIO_VIDEO_BUCKET);

            // 如果存在
            if (lastOwnership != null)
            {
                String lastFileKey = lastOwnership.getObjectKey();

                // 从minio中查询是否存在，如果存在说明已经被用过了，就记录占用大小和使用时间
                ResponseVO <Long> probeResult = videoFileFeignClient.probeVideoFileSize(lastFileKey);
                Integer resultCode = probeResult.getCode();

                // 1. 如果minio中无记录
                if (resultCode.equals(ResponseCode.NOT_FOUND.getCode()))
                {
                    // a. 如果 used=0 ，没有被用过
                    if (lastOwnership.getUsed().equals(0))
                    {
                        // 复用这个key
                        key = lastFileKey;

                        // 更改最后一条记录的 created_time，这一步是为了让后续记录上传额度时，记录正确的时间
                        mediaOwnershipMapper.updateCreatedTimeById(lastOwnership.getId(), now);
                    }

                    // b. 如果 used=1 这就很奇怪了
                    else
                    {
                        // 打个日志
                        log.warn("Found used=1 in last ownership record, which should be 0, id={}", lastOwnership.getId());
                    }
                }
                // 2. 如果有记录
                else if (resultCode.equals(ResponseCode.SUCCESS.getCode()))
                {
                    // 记录下文件大小，用于后续redis记录额度
                    lastFileSize = probeResult.getData();

                    // 记录下时间
                    lastCreatedTime = lastOwnership.getCreatedTime();
                }
                else
                {
                    throw new RuntimeException("请求文件记录失败");
                }
            }

            // 走到这里，有可能是多种情况
            // 如果是最后一个video的key不存在，说明是第一条记录，会直接跳到这里
            // 如果最后一个key已经被使用，这里需要创建新的key，最后一个video的key对应的大小已经算好
            // 如果是最后一个key还没被使用，将会跳过生成新的key这一步，也不用记录最后一个video文件大小

            // 如果不能复用最后一个video的key
            if (key == null)
            {
                // 生成一个新的key

                // 生成一个30位的随机字符串作为文件名
                String fileName = RandomStringUtils.insecure().nextAlphanumeric(30);

                // 根据开始保存的日期，转化为 yyyyMMdd 格式作为路径的一部分
                String curDate = now.toLocalDate().format(DateTimeFormatter.ofPattern(DatePattern.DATE));

                // 拼成完整的key
                key = String.join("/", curDate, fileName);

                // 将新的记录插入文件从属表
                MediaOwnership mediaOwnership = new MediaOwnership();
                mediaOwnership.setObjectKey(key);
                mediaOwnership.setBucket(MinioBucket.MINIO_VIDEO_BUCKET);
                mediaOwnership.setOwnerId(userId);
                mediaOwnership.setCreatedTime(now);
                mediaOwnershipMapper.insert(mediaOwnership);

                // 如果最后一个文件的限额不是0
                if (lastFileSize != 0L)
                {
                    // 计算用户在指定日期下的用量，如果用户超额（逻辑上几乎没有发生的可能，只是为了以防万一），内部会打日志的
                    reserve(userId, UploadQuotaType.VIDEO, lastFileSize, lastCreatedTime.toLocalDate(), false);
                }
            }
        }
        finally
        {
            // 把所的锁等待时间改回去
            // 设置一个【自动恢复】：如果原始时间比30s小，兜底设置为30s，避免修改失败导致session锁超时等待时间一直为5s
            userUploadVideoLockMapper.setSessionInnodbLockWaitTimeout(original_innodb_lock_wait_timeout < 30 ? 30 : original_innodb_lock_wait_timeout);
        }

        return key;
    }

    /**
     * 记录指定用户当天的上传额度<hr/>
     *
     * @param strictMode 如果为true，则表示严格模式，即如果上传额度已超限则抛出异常，否则只记录日志<br/>
     */
    public String reserve(long userId, UploadQuotaType type, long uploadSize, boolean strictMode)
    {
        return reserve(userId, type, uploadSize, LocalDate.now(), strictMode);
    }

    /**
     * 记录指定用户的上传额度<hr/>
     *
     * @param strictMode 如果为true，则表示严格模式，即如果上传额度已超限则抛出异常，否则只记录日志<br/>
     */
    public String reserve(long userId, UploadQuotaType type, long uploadSize, LocalDate date, boolean strictMode)
    {
        if (uploadSize < 0)
        {
            throw new BusinessException("上传文件大小非法");
        }

        long limit = getLimitBytes(type);
        String date1 = date.format(DateTimeFormatter.ofPattern(DatePattern.DATE));

        String key = RedisKey.UPLOAD_QUOTA_PREFIX + type.getKeyPart() + ":size:" + userId + ":" + date1;
        long ttlSeconds = TimeUtil.getSecondsUntilTomorrow();
        long result = uploadQuotaRedisRepository.reserve(key, uploadSize, limit, ttlSeconds, strictMode);

        if (result == 0L)
        {
            throw new RuntimeException("传入参数不合法");
        }
        else if (result == 2L)
        {
            if (strictMode)
            {
                throw new BusinessException("上传额度已超限");
            }
            else
            {
                log.warn("用户{}在{}的上传额度已超限", userId, date);
            }
        }

        return key;
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
