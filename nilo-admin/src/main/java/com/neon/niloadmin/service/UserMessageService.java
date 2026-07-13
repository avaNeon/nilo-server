package com.neon.niloadmin.service;

import cn.hutool.core.lang.Snowflake;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neon.niloadmin.mapper.UserMessageMapper;
import com.neon.niloadmin.mapper.VideoInfoUploadMapper;
import com.neon.nilocommon.entity.enums.userMessage.MessageType;
import com.neon.nilocommon.entity.po.VideoInfoUpload;
import com.neon.nilocommon.entity.po.userMessage.ExtendJson;
import com.neon.nilocommon.entity.po.userMessage.UserMessage;
import com.neon.nilocommon.entity.query.UserMessageQuery;
import com.neon.nilocommon.entity.query.VideoInfoUploadQuery;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
public class UserMessageService
{
    private final UserMessageMapper <UserMessage, UserMessageQuery> userMessageMapper;

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final Snowflake snowflake;

    private final ObjectMapper objectMapper;

    /**
     * <b>向用户发送视频审核结果系统消息</b>
     *
     * @param videoId 视频ID
     */
    @Async("messageExecutor")
    public CompletableFuture <Void> sendVideoReviewMessage(long videoId, String message)
    {
        VideoInfoUpload videoInfoUpload = videoInfoUploadMapper.selectByVideoId(videoId);
        if (videoInfoUpload == null || videoInfoUpload.getUserId() == null)
        {
            throw new BusinessException("数据库记录错误，没有用户ID！");
        }

        sendSystemMessage(videoInfoUpload.getUserId(), videoId, serializeExtendJson(new ExtendJson(message, null)));

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 向用户发送视频相关的消息
     *
     * @param userId  用户ID
     * @param videoId 视频ID
     * @param message 消息内容
     */
    @Async("messageExecutor")
    public CompletableFuture <Void> sendVideoRelatedMessage(long userId, long videoId, String message)
    {
        sendSystemMessage(userId, videoId, serializeExtendJson(new ExtendJson(message, null)));

        return CompletableFuture.completedFuture(null);
    }


    /**
     * <b>向用户发送一条系统消息</b>
     *
     * @param userId     接收消息的用户ID
     * @param videoId    相关视频ID
     * @param extendJson 扩展内容
     */
    private void sendSystemMessage(long userId, long videoId, String extendJson)
    {
        long messageId = snowflake.nextId();
        LocalDateTime createdTime = LocalDateTime.now();
        userMessageMapper.insert(new UserMessage(messageId,
                                                 userId,
                                                 videoId,
                                                 MessageType.SYSTEM.getValue(),
                                                 null,
                                                 null,
                                                 0,
                                                 createdTime,
                                                 extendJson));
    }

    private String serializeExtendJson(ExtendJson extendJson)
    {
        try
        {
            return objectMapper.writeValueAsString(extendJson);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalStateException("序列化用户消息扩展内容失败", e);
        }
    }
}
