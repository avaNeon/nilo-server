package com.neon.nilomqconsumer.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.po.redis.HeartbeatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Repository;

import java.util.List;

@RequiredArgsConstructor
@Repository
public class HeartbeatRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 将 <b>心跳统计信息</b> 和 <b>活跃视频信息</b> 批量写入 Redis<hr/>
     * <p>使用pipeline批量发送给redis处理</p>
     *
     * @param heartbeatMessages 心跳信息列表
     * @param activeVideoKeys   活跃视频的记录名称列表
     */
    public void saveHeartbeatBatch(List <HeartbeatMessage> heartbeatMessages, List <String> activeVideoKeys)
    {
        if (heartbeatMessages.isEmpty() && activeVideoKeys.isEmpty())
        {
            return;
        }

        redisTemplate.executePipelined(new SessionCallback <Object>()
        {

            @Override
            public <K, V> Object execute(RedisOperations <K, V> operations) throws DataAccessException
            {
                // 这里的operations类型应该符合 redisTemplate，我们帮它强转一下
                RedisOperations <String, Object> typeOperations = (RedisOperations <String, Object>) operations;

                for (HeartbeatMessage heartbeatMessage : heartbeatMessages)
                {
                    // 获取对应视频ZSET的key
                    String key = RedisKey.VIDEO_HEARTBEAT_PREFIX + heartbeatMessage.getVideoId() + ":" + heartbeatMessage.getFileIndex();

                    typeOperations.opsForZSet()
                                  .add(key, heartbeatMessage.getSessionId(), Double.parseDouble(heartbeatMessage.getTimestamp()));
                }

                for (String activeVideoKey : activeVideoKeys)
                {
                    typeOperations.opsForSet().add(RedisKey.VIDEO_ACTIVE_LIST, activeVideoKey);
                }

                return null;
            }
        });
    }
}
