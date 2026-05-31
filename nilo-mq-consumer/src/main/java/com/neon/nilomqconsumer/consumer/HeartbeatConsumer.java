package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.po.redis.HeartbeatMessage;
import com.neon.nilomqconsumer.repository.redis.HeartbeatRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static com.neon.nilocommon.entity.constants.MqInfo.VIDEO_HEARTBEAT_QUEUE;

@RequiredArgsConstructor
@Component
public class HeartbeatConsumer
{
    private final HeartbeatRedisRepository heartbeatRedisRepository;

    @RabbitListener(queues = VIDEO_HEARTBEAT_QUEUE)
    public void processHeartbeat(List <String> messages)
    {
        // 将信息转化为对象，方便使用
        List <HeartbeatMessage> heartbeatMessages = messages.stream().map(message ->
                                                                          {
                                                                              String[] parts = message.split(":");
                                                                              if (parts.length == 4)
                                                                              {
                                                                                  return new HeartbeatMessage(parts[0],
                                                                                                              parts[1],
                                                                                                              parts[2],
                                                                                                              parts[3]);
                                                                              }
                                                                              else
                                                                              {
                                                                                  return null;
                                                                              }
                                                                          }).filter(Objects::nonNull).toList();

        // 活跃视频键
        // 统计出所有涉及到的视频文件，并计算出对应的 redis key（nilo:video:heartbeat:videoId:fileIndex）
        List <String> activeVideoKeys = heartbeatMessages.stream()
                                                         .map(item -> RedisKey.VIDEO_HEARTBEAT_PREFIX + item.getVideoId() + ":" + item.getFileIndex())
                                                         .distinct()
                                                         .toList();

        heartbeatRedisRepository.saveHeartbeatBatch(heartbeatMessages, activeVideoKeys);
    }
}
