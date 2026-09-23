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
 * 字幕块消费者<hr/>
 * <p>canal 监听到 video_info_file 增删改后发消息过来。审核通过时分P是按 videoId 全删全插的，
 * 加分P、删分P、换文件都会走到这里。</p>
 * <p>只有重建没有删除：一律照库里现有的分P重灌一遍，视频真没了就是删掉旧块再写 0 条。
 * 审核通过时文件先搬到 public/ 再提交事务，所以收到消息时字幕已经能读到了。</p>
 * <p>重建一个长视频要读 MinIO 再逐块向量化，比其它消费者慢得多，Feign 超时要配够。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SubtitleIndexConsumer
{
    private final InnerAiIndexFeignClient innerAiIndexFeignClient;

    @RabbitListener(queues = MqInfo.AI_SUBTITLE_INDEX_QUEUE)
    public void receiveMessage(VideoIndexTaskDTO videoIndexTaskDTO)
    {
        if (videoIndexTaskDTO == null || videoIndexTaskDTO.getVideoId() == null)
        {
            throw new RuntimeException("字幕块任务不完整，videoIndexTaskDTO = " + videoIndexTaskDTO);
        }

        Long videoId = videoIndexTaskDTO.getVideoId();
        ResponseVO <Integer> responseVO = innerAiIndexFeignClient.indexSubtitle(videoId);

        if (!responseVO.getStatus().equals(ResponseVO.STATUS_SUCCESS))
        {
            throw new RuntimeException("字幕块重建失败，videoId = " + videoId);
        }
    }
}
