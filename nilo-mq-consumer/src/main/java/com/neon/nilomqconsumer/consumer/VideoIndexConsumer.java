package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.mq.VideoIndexTaskDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilomqconsumer.feign.ai.InnerAiIndexFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 视频向量消费者<hr/>
 * <p>canal 监听到 video_info 增删改后发消息过来：发布或改了标题、标签、简介就重建，下架或删除就清掉。
 * 这份向量只用标题、标签、简介，和分P无关，所以分P增删不走这里，看 {@link SubtitleIndexConsumer}。</p>
 * <p>重建走「先删后写」，重复投递不会留下脏数据。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class VideoIndexConsumer
{
    private final InnerAiIndexFeignClient innerAiIndexFeignClient;

    @RabbitListener(queues = MqInfo.AI_VIDEO_INDEX_QUEUE)
    public void receiveMessage(VideoIndexTaskDTO videoIndexTaskDTO)
    {
        if (videoIndexTaskDTO == null || videoIndexTaskDTO.getVideoId() == null || videoIndexTaskDTO.getVideoIndexTaskType() == null)
        {
            throw new RuntimeException("视频向量任务不完整，videoIndexTaskDTO = " + videoIndexTaskDTO);
        }

        Long videoId = videoIndexTaskDTO.getVideoId();
        ResponseVO <?> responseVO = switch (videoIndexTaskDTO.getVideoIndexTaskType())
        {
            case UPSERT -> innerAiIndexFeignClient.indexVideo(videoId);
            case DELETE -> innerAiIndexFeignClient.deleteVideo(videoId);
        };

        if (!responseVO.getStatus().equals(ResponseVO.STATUS_SUCCESS))
        {
            throw new RuntimeException("视频向量处理失败，videoId = " + videoId + "，type = " + videoIndexTaskDTO.getVideoIndexTaskType());
        }
    }
}
