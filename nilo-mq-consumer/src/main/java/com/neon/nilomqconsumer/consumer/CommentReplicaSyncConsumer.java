package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.UserSnapshotDTO;
import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import com.neon.nilocommon.entity.dto.mq.UserReplicaSyncDTO;
import com.neon.nilocommon.entity.dto.mq.VideoReplicaSyncDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilomqconsumer.feign.comment.InnerVideoCommentFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 评论库副本表同步消费者<hr/>
 * <p>canal 监听到 video_info、user_info 新增、更新或删除后发消息过来，这里调评论服务同步 video_info_replica、user_info_replica。</p>
 * <p>同一个视频（用户）的新增和删除必须按发生顺序执行，所以每个队列只用一个消费者。</p>
 * <p>评论服务的新增是「新增或覆盖」、删除在行不存在时什么都不做，重复投递不会出问题。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CommentReplicaSyncConsumer
{
    private final InnerVideoCommentFeignClient innerVideoCommentFeignClient;

    @RabbitListener(queues = MqInfo.COMMENT_VIDEO_REPLICA_QUEUE, concurrency = "1")
    public void receiveVideo(VideoReplicaSyncDTO dto)
    {
        if (dto == null || dto.getType() == null || dto.getVideo() == null || dto.getVideo().getVideoId() == null)
        {
            throw new IllegalArgumentException("视频副本消息不完整，dto = " + dto);
        }

        VideoSnapshotDTO video = dto.getVideo();
        Long videoId = video.getVideoId();
        ResponseVO <Void> result = switch (dto.getType())
        {
            case UPSERT -> innerVideoCommentFeignClient.upsertVideoReplica(video);
            case DELETE -> innerVideoCommentFeignClient.deleteVideoReplica(videoId);
        };
        assertSuccess(result, "同步评论库视频副本失败，videoId = " + videoId + "，type = " + dto.getType());
    }

    @RabbitListener(queues = MqInfo.COMMENT_USER_REPLICA_QUEUE, concurrency = "1")
    public void receiveUser(UserReplicaSyncDTO dto)
    {
        if (dto == null || dto.getType() == null || dto.getUser() == null || dto.getUser().getUserId() == null)
        {
            throw new IllegalArgumentException("用户副本消息不完整，dto = " + dto);
        }

        UserSnapshotDTO user = dto.getUser();
        Long userId = user.getUserId();
        ResponseVO <Void> result = switch (dto.getType())
        {
            case UPSERT -> innerVideoCommentFeignClient.upsertUserReplica(user);
            case DELETE -> innerVideoCommentFeignClient.deleteUserReplica(userId);
        };
        assertSuccess(result, "同步评论库用户副本失败，userId = " + userId + "，type = " + dto.getType());
    }

    /**
     * 判断 RPC 结果是否成功
     *
     * @param result       RPC 结果
     * @param errorMessage 错误信息
     */
    private void assertSuccess(ResponseVO <Void> result, String errorMessage)
    {
        if (result == null || !ResponseCode.SUCCESS.getCode().equals(result.getCode()))
        {
            String detail = result == null ? "评论服务调用失败" : result.getInfo();
            throw new RuntimeException(errorMessage + "，" + detail);
        }
    }
}
