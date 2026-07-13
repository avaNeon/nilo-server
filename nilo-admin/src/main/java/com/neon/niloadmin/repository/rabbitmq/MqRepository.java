package com.neon.niloadmin.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.comment.CommentArchiveDTO;
import com.neon.nilocommon.entity.dto.comment.CommentRedundantDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class MqRepository
{
    private static final int BATCH_SIZE = 50;

    private final RabbitTemplate rabbitTemplate;

    /**
     * 将视频key列表放入视频删除队列
     *
     * @param keys 视频key列表
     */
    public void addKeysToVideoDeleteQueue(List <String> keys)
    {
        for (String key : keys)
        {
            rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_VIDEO_DELETE_ROUTING_KEY, key);
        }
    }

    /**
     * 将图片key列表放入图片删除队列
     *
     * @param keys 图片key列表
     */
    public void addKeysToImageDeleteQueue(List <String> keys)
    {
        if (keys == null || keys.isEmpty())
        {
            return;
        }

        for (int i = 0 ; i < keys.size() ; i += BATCH_SIZE)
        {
            int end = Math.min(i + BATCH_SIZE, keys.size());
            List <String> batch = new ArrayList <>(keys.subList(i, end));
            rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_IMAGE_DELETE_ROUTING_KEY, batch);
        }
    }

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
