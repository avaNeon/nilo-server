package com.neon.niloweb.repository.rabbitmq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.neon.nilocommon.entity.constants.MqInfo.VIDEO_HEARTBEAT_EXCHANGE;
import static com.neon.nilocommon.entity.constants.MqInfo.VIDEO_HEARTBEAT_ROUTING_KEY;

@RequiredArgsConstructor
@Repository
public class VideoOnlineMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    public void sendHeartbeat(List<String> messages)
    {
        rabbitTemplate.convertAndSend(VIDEO_HEARTBEAT_EXCHANGE, VIDEO_HEARTBEAT_ROUTING_KEY, messages);
    }
}
