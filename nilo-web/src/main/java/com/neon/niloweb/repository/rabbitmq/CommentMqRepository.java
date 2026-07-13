package com.neon.niloweb.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.comment.CommentArchiveDTO;
import com.neon.nilocommon.entity.dto.comment.CommentRedundantDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class CommentMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送评论冗余字段更新消息
     *
     * @param dto 需要同步的冗余字段（非空字段才会被消费端处理）
     */
    public void sendCommentRedundantUpdate(CommentRedundantDTO dto)
    {
        if (dto == null)
        {
            return;
        }
        rabbitTemplate.convertAndSend(MqInfo.COMMENT_EXCHANGE, MqInfo.COMMENT_UPDATE_ROUTING_KEY, dto);
    }

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
