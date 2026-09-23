package com.neon.niloai.consumer;

import com.neon.niloai.service.VideoVectorIndexService;
import com.neon.nilocommon.entity.dto.mq.VideoIndexTaskDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.neon.nilocommon.entity.constants.MqInfo.AI_VIDEO_INDEX_QUEUE;

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
    private final VideoVectorIndexService videoVectorIndexService;

    @RabbitListener(queues = AI_VIDEO_INDEX_QUEUE)
    public void consume(VideoIndexTaskDTO dto)
    {
        if (dto == null || dto.getVideoId() == null || dto.getVideoIndexTaskType() == null)
        {
            throw new IllegalArgumentException("视频向量任务不完整: " + dto);
        }
        Long videoId = dto.getVideoId();
        switch (dto.getVideoIndexTaskType())
        {
            case UPSERT -> videoVectorIndexService.indexVideo(videoId);
            case DELETE ->
            {
                videoVectorIndexService.deleteVideo(videoId);
                log.info("视频向量已删除, videoId={}", videoId);
            }
        }
    }
}
