package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Date;


/**
 * 用户视频行为 点赞、收藏、投币
 */
@Getter
@Setter
@ToString
public class UserVideoAction
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
     * 视频用户ID
     */
    private Long videoUserId;

    /**
     * 1:视频点赞 2:视频收藏 3:视频投币
     */
    private Short actionType;

    /**
     * 投币数量
     */
    private Short coinAmount;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 操作时间
     */
    private LocalDateTime actionTime;
}
