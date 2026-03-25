package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.UploadedVideoFileDTO;
import com.neon.niloweb.config.WebConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@Repository
public class UploadRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    private final WebConfig webConfig;

    /**
     * 从Redis中获取预上传记录
     *
     * @param userId   用户ID
     * @param uploadId 上传ID
     * @return 记录信息
     */
    public UploadedVideoFileDTO getPreUploadKey(Long userId, long uploadId)
    {
        return (UploadedVideoFileDTO) redisTemplate.opsForValue().get(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + userId + ":" + uploadId);
    }

    /**
     * 向Redis加入一条预上传记录<hr/>
     * 会自动填写filePath
     *
     * @param video  视频信息
     * @param userId 用户ID
     */
    public void addPreUploadKey(UploadedVideoFileDTO video, long userId)
    {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern(DatePattern.DATE)); // 使用Java8+的时间类生成指定格式的时间字符串
        String filePath = date + "/" + userId + "/" + video.getUploadId();
        /*
         * 最终的文件层次是这样的：
         * file
         *   -tmp
         *     -<date日期>
         *       -<用户id-1>
         *         -<uploadId-1.1>
         *         -<uploadId-1.2>
         *       -<用户id-2>
         *         -<uploadId-2.1>
         *         -<uploadId-2.2>
         */
        String absolutePath = webConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + Constants.TMP_FOLDER_NAME + "/" + filePath;
        File videoFile = new File(absolutePath);
        if (!videoFile.exists())
        {
            videoFile.mkdirs();
        }
        video.setFilePath(filePath);
        // 使用指定 KEY名+用户id 作为Redis键名，有效时长1天
        redisTemplate.opsForValue()
                     .set(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + userId + ":" + video.getUploadId(), video, Duration.ofDays(1L));
    }

    /**
     * 更新Redis中预上传视频记录信息
     *
     * @param video  视频信息（需要设置uploadId）
     * @param userId 用户ID
     */
    public void updatePreUploadKey(UploadedVideoFileDTO video, long userId)
    {
        redisTemplate.opsForValue()
                     .set(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + userId + ":" + video.getUploadId(), video, Duration.ofDays(1L));

    }

    /**
     * 删除Redis中预上传视频记录
     *
     * @param userId   用户ID
     * @param uploadId 上传ID
     */
    public void deletePreUploadKey(long userId, long uploadId)
    {
        redisTemplate.delete(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + userId + ":" + uploadId);
    }

}
