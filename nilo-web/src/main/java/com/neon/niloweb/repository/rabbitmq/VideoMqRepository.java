package com.neon.niloweb.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@RequiredArgsConstructor
@Repository
public class VideoMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * 批量将视频文件的删除任务添加至MQ <hr/>
     * <p>将List拆分为单个路径，一个路径对应一个message传给MQ</p>
     * <p>因为这个操作的性能瓶颈在磁盘删除操作，所以即使拆分为单个路径这个性能损失也不算严重（就目前而言）</p>
     *
     * @param filePathList 一个列表，元素为要删除的文件路径
     */
    public void addVideoFilesToVideoDeleteQueue(List <String> filePathList)
    {
        for (String path : filePathList)
        {
            rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_VIDEO_DELETE_ROUTING_KEY, path);
        }
    }

    /**
     * 批量将视频文件的转码任务添加至MQ <hr/>
     * <p>将List拆分为单个路径，一个路径对应一个message传给MQ</p>
     * <p>转码的CPU/GPU消耗比删除操作还要高得多，所以还是一个一个发性能损失也不严重</p>
     *
     * @param fileUploadList 一个列表，元素为要转码的视频文件的bean
     */
    public void addVideoFilesToTranscodingQueue(List <VideoInfoFileUpload> fileUploadList)
    {
        for (VideoInfoFileUpload fileUpload : fileUploadList)
        {
            rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_TRANSCODING_ROUTING_KEY, fileUpload);
        }
    }
}
