package com.neon.nilocommon.entity.query;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


/**
 * 用户视频行为 点赞、收藏、投币参数
 */
@Setter
@Getter
@ToString
public class UserVideoActionQuery extends BaseQuery
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
    private Integer coinAmount;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 操作时间
     */
    private String actionTime;

    private String actionTimeStart;

    private String actionTimeEnd;
}
