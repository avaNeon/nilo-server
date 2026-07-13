package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.comment.CommentArchiveDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.comment.OperationType;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilomqconsumer.feign.comment.InnerVideoCommentFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CommentArchiveConsumer
{
    private final InnerVideoCommentFeignClient innerVideoCommentFeignClient;

    /**
     * 视频评论归档/恢复/彻底删除消费者<hr/>
     * <p>由 admin/web 删除、恢复、彻底删除视频时在本地事务提交后发出，保证评论服务侧数据的最终一致性</p>
     * <p>并发度固定为1，保证同一视频的多次操作按发送顺序串行处理，避免归档/恢复乱序导致数据不一致</p>
     *
     * @param dto 归档操作消息
     */
    @RabbitListener(queues = MqInfo.COMMENT_ARCHIVE_QUEUE, concurrency = "1")
    public void receiveMessage(CommentArchiveDTO dto)
    {
        if (dto == null || dto.getVideoId() == null || dto.getOperationType() == null)
        {
            throw new IllegalArgumentException("CommentArchiveDTO 参数不合法：" + dto);
        }

        Long videoId = dto.getVideoId();
        OperationType operationType = dto.getOperationType();

        ResponseVO <Void> result;
        String errorMessage;
        if (operationType == OperationType.ARCHIVE)
        {
            result = innerVideoCommentFeignClient.archiveByVideoId(videoId);
            errorMessage = "归档评论失败";
        }
        else if (operationType == OperationType.RECOVERY)
        {
            result = innerVideoCommentFeignClient.restoreByVideoId(videoId);
            errorMessage = "恢复评论失败";
        }
        else if (operationType == OperationType.DESTROY)
        {
            result = innerVideoCommentFeignClient.purgeArchiveByVideoId(videoId);
            errorMessage = "彻底删除评论归档失败";
        }
        else
        {
            throw new IllegalArgumentException("未知的操作类型：" + operationType);
        }

        if (result == null || !ResponseCode.SUCCESS.getCode().equals(result.getCode()))
        {
            throw new RuntimeException(errorMessage + "，videoId=" + videoId + "，详情：" + (result == null ? "无响应" : result.getInfo()));
        }
    }
}
