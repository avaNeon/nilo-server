package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.comment.CommentRedundantDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilomqconsumer.feign.comment.InnerVideoCommentFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CommentUpdateConsumer
{
    private final InnerVideoCommentFeignClient innerVideoCommentFeignClient;

    /**
     * 评论冗余字段更新消费者<hr/>
     * <p>按 {@link CommentRedundantDTO} 中非空字段调用对应同步接口。</p>
     *
     * @param dto 冗余字段更新消息
     */
    @RabbitListener(queues = MqInfo.COMMENT_UPDATE_QUEUE)
    public void receiveMessage(CommentRedundantDTO dto)
    {
        if (dto == null)
        {
            throw new IllegalArgumentException("CommentRedundantDTO is null");
        }

        if (dto.getVideoId() != null)
        {
            if (dto.getVideoName() != null && !dto.getVideoName().isBlank())
            {
                assertSuccess(innerVideoCommentFeignClient.updateVideoNameByVideoId(dto.getVideoId(), dto.getVideoName()),
                              "同步评论视频标题失败");
            }
            if (dto.getVideoCover() != null && !dto.getVideoCover().isBlank())
            {
                assertSuccess(innerVideoCommentFeignClient.updateVideoCoverByVideoId(dto.getVideoId(), dto.getVideoCover()),
                              "同步评论视频封面失败");
            }
        }

        if (dto.getUserId() != null)
        {
            if (dto.getNickName() != null && !dto.getNickName().isBlank())
            {
                assertSuccess(innerVideoCommentFeignClient.updateNickNameByUserId(dto.getUserId(), dto.getNickName()),
                              "同步评论用户昵称失败");
            }
            if (dto.getAvatar() != null && !dto.getAvatar().isBlank())
            {
                assertSuccess(innerVideoCommentFeignClient.updateAvatarByUserId(dto.getUserId(), dto.getAvatar()),
                              "同步评论用户头像失败");
            }
        }
    }

    private void assertSuccess(ResponseVO <Void> result, String errorMessage)
    {
        if (result == null || !ResponseCode.SUCCESS.getCode().equals(result.getCode()))
        {
            String detail = result == null ? "评论服务调用失败" : result.getInfo();
            throw new RuntimeException(errorMessage + "，" + detail);
        }
    }
}
