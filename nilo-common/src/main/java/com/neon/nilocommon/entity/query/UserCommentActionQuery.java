package com.neon.nilocommon.entity.query;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户评论行为 点赞、点踩参数
 */
@Setter
@Getter
@ToString
public class UserCommentActionQuery extends BaseQuery
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
    private String actionTime;

    private String actionTimeStart;

    private String actionTimeEnd;

}
