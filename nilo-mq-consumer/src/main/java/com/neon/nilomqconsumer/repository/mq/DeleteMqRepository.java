package com.neon.nilomqconsumer.repository.mq;

import com.neon.nilocommon.entity.constants.MqInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@RequiredArgsConstructor
@Repository
public class DeleteMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * 将文件的删除任务添加至MQ <hr/>
     * 将List拆分为单个路径，一个路径对应一个message传给MQ<br/>
     * 因为这个操作的性能瓶颈在磁盘删除操作，所以即使拆分为单个路径这个性能损失也不算严重（就目前而言）
     *
     * @param filePathList 一个列表，元素为要删除的文件路径
     */
    public void addVideoFile2DeleteQueue(List <String> filePathList)
    {
        for (String path : filePathList)
        {
            rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_DELETE_ROUTING_KEY, path);
        }
    }

    /**
     * 将单个文件的删除任务添加至MQ <hr/>
     * @param filePath 一个文件路径
     */
    public void addVideoFile2DeleteQueue(String filePath)
    {
        rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_DELETE_ROUTING_KEY, filePath);
    }
}
