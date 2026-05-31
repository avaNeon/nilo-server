package com.neon.niloweb.service;

import cn.hutool.core.lang.Snowflake;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neon.nilocommon.entity.dto.UserMessageCount;
import com.neon.nilocommon.entity.dto.UserMessageDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userMessage.MessageType;
import com.neon.nilocommon.entity.enums.userVideoAction.VideoActionType;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.po.userMessage.ExtendJson;
import com.neon.nilocommon.entity.po.userMessage.UserMessage;
import com.neon.nilocommon.entity.query.UserMessageQuery;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.EnumFieldChecker;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.UserMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
public class UserMessageService
{
    private final UserMessageMapper <UserMessage, UserMessageQuery> userMessageMapper;

    private final Snowflake snowflake;

    private final ObjectMapper objectMapper;

    private final WebConfig webConfig;

    /**
     * 获取用户未读统计信息
     *
     * @param userId 用户ID
     * @return 用户未读统计信息，如果查询结果为空，返回不含数据的对象
     */
    public UserMessageCount getUncheckedMessageCount(long userId)
    {
        UserMessageCount userMessageCount = userMessageMapper.selectUncheckedMessageCount(userId);
        return Objects.requireNonNullElseGet(userMessageCount, UserMessageCount::new);
    }

    /**
     * 将指定类型未读消息都标记为已读
     *
     * @param userId      用户ID
     * @param messageType 消息类型
     */
    public void checkAllMessages(long userId, short messageType)
    {
        // 校验类型是否合法
        boolean exists = EnumFieldChecker.containsFieldValue(MessageType.class, "value", messageType);
        if (!exists)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        userMessageMapper.checkAllMessages(userId, messageType);
    }

    /**
     * 获取一个分类的详细总数
     *
     * @param userId      用户ID
     * @param messageType 消息类型
     * @return 消息总数
     */
    public int getMessageCount(long userId, short messageType)
    {
        UserMessageQuery query = new UserMessageQuery();
        query.setUserId(userId);
        query.setMessageType(messageType);

        // 查询结果
        Integer count = userMessageMapper.selectCount(query);

        // 如果结果不存在，返回0
        return Objects.requireNonNullElse(count, 0);
    }

    /**
     * 分页获取指定类型的消息
     *
     * @param userId      用户ID
     * @param messageType 消息类型
     * @return 消息列表
     */
    public List <UserMessageDTO> getMessages(long userId, short messageType, int pageNo)
    {
        // 校验消息类型是否合法
        Optional <MessageType> optionalMessageType = EnumFieldChecker.findByFieldValue(MessageType.class, "value", messageType);
        if (optionalMessageType.isEmpty())
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        int pageSize = webConfig.getPageSize();
        int start = (pageNo - 1) * pageSize;

        UserMessageQuery query = new UserMessageQuery();
        query.setUserId(userId);
        query.setMessageType(messageType);
        query.setPageNo(start);
        query.setPageSize(pageSize);

        return userMessageMapper.selectDtoList(query);
    }

    /**
     * 删除消息
     *
     * @param userId    用户ID
     * @param messageId 消息ID
     */
    public void deleteMessage(long userId, long messageId)
    {
        userMessageMapper.deleteByUserIdAndMessageId(userId, messageId);
    }

    /**
     * <b>向视频发布者通知其它用户对视频的操作</b><hr/>
     * <p>只有<b>点赞</b>和<b>收藏</b>操作会通知用户</p>
     * <p>只记录<b>一次</b>，之后的取消、再重复操作都不会再记录</p>
     *
     * @param videoInfo    被操作的视频信息
     * @param senderUserId 操作用户ID
     * @param actionType   操作类型
     */
    @Async("messageExecutor")
    public CompletableFuture <Void> recordVideoActionMessage(VideoInfo videoInfo, long senderUserId, VideoActionType actionType)
    {
        MessageType messageType = switch (actionType)
        {
            case LIKE -> MessageType.LIKE;
            case COLLECT -> MessageType.COLLECT;
            case COIN -> null;
        };

        // 如果没能获得有效操作，什么都不干
        if (messageType == null)
        {
            return CompletableFuture.completedFuture(null);
        }

        insertIfNotExistsUserMessage(videoInfo.getUserId(), videoInfo.getVideoId(), messageType, senderUserId);

        return CompletableFuture.completedFuture(null);
    }

    /**
     * <b>向用户通知其他人在回复了TA的评论/在视频下留言</b>
     *
     * @param receiverUserId      接收者用户ID
     * @param senderUserId        发送者用户ID
     * @param videoId             视频ID
     * @param commentContent      评论内容
     * @param replyCommentContent 回复的评论的内容
     */
    @Async("messageExecutor")
    public CompletableFuture <Void> recordCommentMessage(long receiverUserId,
                                                         long senderUserId,
                                                         long videoId,
                                                         String commentContent,
                                                         String replyCommentContent)
    {
        ExtendJson extendJson = new ExtendJson(commentContent, replyCommentContent);

        try
        {
            insertUserMessage(receiverUserId,
                              videoId,
                              MessageType.COMMENT,
                              senderUserId,
                              objectMapper.writeValueAsString(extendJson));
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException(e);
        }


        return CompletableFuture.completedFuture(null);
    }

    /**
     * <b>插入一条用户信息</b><hr/>
     *
     * @param userId       用户ID
     * @param videoId      相关视频ID
     * @param messageType  信息类型
     * @param senderUserId 发送者用户ID
     */
    private void insertUserMessage(long userId, long videoId, MessageType messageType, Long senderUserId, String extendJson)
    {
        long messageId = snowflake.nextId();
        LocalDate createdTime = LocalDate.now();

        userMessageMapper.insert(new UserMessage(messageId,
                                                 userId,
                                                 videoId,
                                                 messageType.getValue(),
                                                 senderUserId,
                                                 0,
                                                 createdTime,
                                                 extendJson));
    }

    /**
     * <b>不重复插入一条用户信息</b><hr/>
     * <p>如果数据已经存在，不再重复插入</p>
     *
     * @param userId       用户ID
     * @param videoId      相关视频ID
     * @param messageType  信息类型
     * @param senderUserId 发送者用户ID
     */
    private void insertIfNotExistsUserMessage(long userId, long videoId, MessageType messageType, Long senderUserId)
    {
        long messageId = snowflake.nextId();
        LocalDate createdTime = LocalDate.now();

        userMessageMapper.insertIfNotExists(new UserMessage(messageId,
                                                            userId,
                                                            videoId,
                                                            messageType.getValue(),
                                                            senderUserId,
                                                            0,
                                                            createdTime,
                                                            null));
    }
}
