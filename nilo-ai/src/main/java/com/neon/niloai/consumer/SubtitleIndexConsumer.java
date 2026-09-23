package com.neon.niloai.consumer;

import com.neon.niloai.service.SubtitleChunkService;
import com.neon.nilocommon.entity.dto.mq.VideoIndexTaskDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.neon.nilocommon.entity.constants.MqInfo.AI_SUBTITLE_INDEX_QUEUE;

/**
 * 字幕块消费者<hr/>
 * <p>canal 监听到 video_info_file 增删改后发消息过来。审核通过时分P是按 videoId 全删全插的，
 * 加分P、删分P、换文件都会走到这里。</p>
 * <p>只有重建没有删除：一律照库里现有的分P重灌一遍，视频真没了就是删掉旧块再写 0 条。
 * 审核通过时文件先搬到 public/ 再提交事务，所以收到消息时字幕已经能读到了。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SubtitleIndexConsumer
{
    private final SubtitleChunkService subtitleChunkService;

    @RabbitListener(queues = AI_SUBTITLE_INDEX_QUEUE)
    public void consume(VideoIndexTaskDTO dto)
    {
        if (dto == null || dto.getVideoId() == null)
        {
            throw new IllegalArgumentException("字幕块任务不完整: " + dto);
        }
        subtitleChunkService.indexVideo(dto.getVideoId());
    }
}
