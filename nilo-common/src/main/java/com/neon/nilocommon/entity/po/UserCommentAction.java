package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;


/**
 * 用户评论行为 点赞、点踩
 */
@Getter
@Setter
@ToString
public class UserCommentAction
{

    /**
     * 自增ID
     */
    private Long actionId;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 评论ID
     */
    private Long commentId;

    /**
     * 0:评论点赞 1:评论点踩
     */
    private Integer actionType;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 操作时间
     */
    private LocalDateTime actionTime;
}
