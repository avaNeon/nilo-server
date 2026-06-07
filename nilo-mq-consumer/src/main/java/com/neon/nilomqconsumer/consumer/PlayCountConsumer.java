package com.neon.nilomqconsumer.consumer;

import com.neon.nilomqconsumer.service.PlayCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.neon.nilocommon.entity.constants.MqInfo.PLAY_COUNT_QUEUE;

@RequiredArgsConstructor
@Component
public class PlayCountConsumer
{
    private final PlayCountService playCountService;

    @RabbitListener(queues = PLAY_COUNT_QUEUE, concurrency = "1")
    public void consume(Map <Long, Integer> playCountMap)
    {
        playCountService.flushPlayCount(playCountMap);
    }
}
