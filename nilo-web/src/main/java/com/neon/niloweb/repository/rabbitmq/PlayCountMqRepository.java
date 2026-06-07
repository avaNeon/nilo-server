package com.neon.niloweb.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@RequiredArgsConstructor
@Repository
public class PlayCountMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    public void sendPlayCountFlushMessage(Map <Long, Integer> playCountMap)
    {
        rabbitTemplate.convertAndSend(MqInfo.PLAY_COUNT_EXCHANGE, MqInfo.PLAY_COUNT_ROUTING_KEY, playCountMap);
    }
}
