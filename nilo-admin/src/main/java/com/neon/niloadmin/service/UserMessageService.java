package com.neon.niloadmin.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.niloadmin.mapper.UserMessageMapper;
import com.neon.niloadmin.mapper.VideoInfoUploadMapper;
import com.neon.nilocommon.entity.enums.userMessage.MessageType;
import com.neon.nilocommon.entity.po.userMessage.UserMessage;
import com.neon.nilocommon.entity.po.VideoInfoUpload;
import com.neon.nilocommon.entity.query.UserMessageQuery;
import com.neon.nilocommon.entity.query.VideoInfoUploadQuery;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
public class UserMessageService
{
    private final UserMessageMapper <UserMessage, UserMessageQuery> userMessageMapper;

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final Snowflake snowflake;

    /**
     * <b>向用户发送视频审核结果系统消息</b>
     *
     * @param videoId    视频ID
     * @param extendJson 审核消息内容
     */
    @Async("messageExecutor")
    public CompletableFuture <Void> sendVideoReviewMessage(long videoId, String extendJson)
    {
        VideoInfoUpload videoInfoUpload = videoInfoUploadMapper.selectByVideoId(videoId);
        if (videoInfoUpload == null || videoInfoUpload.getUserId() == null)
        {
            throw new BusinessException("数据库记录错误，没有用户ID！");
        }

        sendSystemMessage(videoInfoUpload.getUserId(), videoId, extendJson);

        return CompletableFuture.completedFuture(null);
    }

    /**
     * <b>向用户发送一条系统消息</b>
     *
     * @param userId     接收消息的用户ID
     * @param videoId    相关视频ID
     * @param extendJson 扩展JSON
     */
    private void sendSystemMessage(long userId, long videoId, String extendJson)
    {
        long messageId = snowflake.nextId();
        LocalDate createdTime = LocalDate.now();
        userMessageMapper.insert(new UserMessage(messageId,
                                                 userId,
                                                 videoId,
                                                 MessageType.SYSTEM.getValue(),
                                                 null,
                                                 0,
                                                 createdTime,
                                                 extendJson));
    }
}
