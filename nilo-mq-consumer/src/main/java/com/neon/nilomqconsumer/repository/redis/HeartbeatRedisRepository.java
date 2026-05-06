package com.neon.nilomqconsumer.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class HeartbeatRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 将心跳信息写入 Redis
     *
     * @param key       redis key
     * @param sessionId 会话ID
     * @param timestamp 创建时的时间戳
     */
    public void saveHeartbeat(String key, String sessionId, long timestamp)
    {
        // 写入 ZSet O(log(N))
        redisTemplate.opsForZSet().add(key, sessionId, timestamp);
        // 优化：维护一个 Set，包含所有有人在看的视频对应在 ZSET 的 key，方便后续定时清理任务，避免全量扫描 Redis Key
        redisTemplate.opsForSet().add(RedisKey.VIDEO_ACTIVE_LIST, key);
    }
}
