package com.neon.niloadmin.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@RequiredArgsConstructor
@Repository
public class MqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * 将地址列表放入删除队列
     * @param pathList 地址列表
     */
    public void addPathList2DeleteQueue(List <String> pathList)
    {
        for (String path : pathList)
        {
            rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_DELETE_ROUTING_KEY, path);
        }
    }
}
