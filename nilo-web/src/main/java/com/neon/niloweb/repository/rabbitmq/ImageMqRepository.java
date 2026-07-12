package com.neon.niloweb.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class ImageMqRepository
{
    private static final int BATCH_SIZE = 50;

    private final RabbitTemplate rabbitTemplate;

    /**
     * 批量将图片文件的删除任务添加至MQ<hr/>
     * <p>将List按每{@value #BATCH_SIZE}个元素拆分为小批量，一批对应一个message传给MQ</p>
     *
     * @param baseKeyList 图片文件的baseKey列表
     */
    public void addImageFilesToDeleteQueue(List <String> baseKeyList)
    {
        if (baseKeyList == null || baseKeyList.isEmpty())
        {
            return;
        }

        for (int i = 0; i < baseKeyList.size(); i += BATCH_SIZE)
        {
            int end = Math.min(i + BATCH_SIZE, baseKeyList.size());
            List <String> batch = new ArrayList <>(baseKeyList.subList(i, end));
            rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_IMAGE_DELETE_ROUTING_KEY, batch);
        }
    }
}
