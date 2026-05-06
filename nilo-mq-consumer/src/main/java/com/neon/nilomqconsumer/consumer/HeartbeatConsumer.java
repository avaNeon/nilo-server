package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilomqconsumer.repository.redis.HeartbeatRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.neon.nilocommon.entity.constants.MqInfo.VIDEO_HEARTBEAT_QUEUE;

@RequiredArgsConstructor
@Component
public class HeartbeatConsumer
{
    private final HeartbeatRedisRepository heartbeatRedisRepository;

    @RabbitListener(queues = VIDEO_HEARTBEAT_QUEUE)
    public void processHeartbeat(String message)
    {
        // 解析消息 "videoId:fileIndex:userId:timestamp"
        String[] parts = message.split(":");
        if (parts.length == 4)
        {
            String videoId = parts[0];
            String fileIndex = parts[1];
            String sessionId = parts[2];
            long timestamp = Long.parseLong(parts[3]);
            // key 包含了 videoId 和 fileIndex
            // 记录的粒度到每个视频文件
            String key = RedisKey.VIDEO_HEARTBEAT_PREFIX + videoId + ":" + fileIndex;
            // 写入redis
            heartbeatRedisRepository.saveHeartbeat(key, sessionId, timestamp);
        }
    }
}
