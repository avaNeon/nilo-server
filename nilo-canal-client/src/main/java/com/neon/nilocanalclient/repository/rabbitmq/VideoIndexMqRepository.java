package com.neon.nilocanalclient.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.mq.VideoIndexTaskDTO;
import com.neon.nilocommon.entity.enums.videoIndex.VideoIndexTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class VideoIndexMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * 通知 nilo-ai 重建或删除这个视频的标题标签简介向量
     */
    public void sendVideoIndexTask(Long videoId, VideoIndexTaskType type)
    {
        rabbitTemplate.convertAndSend(MqInfo.AI_INDEX_EXCHANGE,
                                      MqInfo.AI_VIDEO_INDEX_ROUTING_KEY,
                                      new VideoIndexTaskDTO(videoId, type));
    }

    /**
     * 通知 nilo-ai 按库里现有的分P重建这个视频的字幕块，分P没了就等于清空
     */
    public void sendSubtitleIndexTask(Long videoId)
    {
        rabbitTemplate.convertAndSend(MqInfo.AI_INDEX_EXCHANGE,
                                      MqInfo.AI_SUBTITLE_INDEX_ROUTING_KEY,
                                      new VideoIndexTaskDTO(videoId, VideoIndexTaskType.UPSERT));
    }
}
