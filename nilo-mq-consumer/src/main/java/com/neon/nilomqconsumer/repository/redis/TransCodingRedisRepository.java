package com.neon.nilomqconsumer.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.UploadedVideoFileDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class TransCodingRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 从Redis中获取预上传记录
     *
     * @param userId   用户ID
     * @param uploadId 上传ID
     * @return 记录信息
     */
    public UploadedVideoFileDTO getPreUploadKey(long userId, long uploadId)
    {
        return (UploadedVideoFileDTO) redisTemplate.opsForValue().get(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + userId + ":" + uploadId);
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
