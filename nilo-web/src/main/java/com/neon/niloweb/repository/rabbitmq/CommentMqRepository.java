package com.neon.niloweb.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.comment.CommentArchiveDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class CommentMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送视频评论归档/恢复/彻底删除消息
     *
     * @param dto 归档操作消息
     */
    public void sendCommentArchiveOperation(CommentArchiveDTO dto)
    {
        if (dto == null)
        {
            return;
        }
        rabbitTemplate.convertAndSend(MqInfo.COMMENT_EXCHANGE, MqInfo.COMMENT_ARCHIVE_ROUTING_KEY, dto);
    }
}
