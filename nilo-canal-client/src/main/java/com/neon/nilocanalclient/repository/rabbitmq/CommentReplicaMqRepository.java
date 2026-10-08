package com.neon.nilocanalclient.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.mq.UserReplicaSyncDTO;
import com.neon.nilocommon.entity.dto.mq.VideoReplicaSyncDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class CommentReplicaMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * 通知评论服务新增、覆盖或删除这个视频的副本
     */
    public void sendVideoReplica(VideoReplicaSyncDTO dto)
    {
        rabbitTemplate.convertAndSend(MqInfo.COMMENT_EXCHANGE, MqInfo.COMMENT_VIDEO_REPLICA_ROUTING_KEY, dto);
    }

    /**
     * 通知评论服务新增、覆盖或删除这个用户的副本
     */
    public void sendUserReplica(UserReplicaSyncDTO dto)
    {
        rabbitTemplate.convertAndSend(MqInfo.COMMENT_EXCHANGE, MqInfo.COMMENT_USER_REPLICA_ROUTING_KEY, dto);
    }
}
