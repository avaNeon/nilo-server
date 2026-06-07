package com.neon.nilocommon.entity.query;

import lombok.Getter;
import lombok.Setter;


/**
 * 用户消息表参数
 */
@Setter
@Getter
public class UserMessageQuery extends BaseQuery
{
    /**
     * 消息ID
     */
    private Long messageId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 相关视频ID
     */
    private Long videoId;

    /**
     * 消息类型
     */
    private Short messageType;

    /**
     * 发送者用户ID
     */
    private Long senderUserId;

    /**
     * 0:未读 1:已读
     */
    private Integer readType;

    /**
     * 创建时间
     */
    private String createTime;

    private String createTimeStart;

    private String createTimeEnd;

    /**
     * 扩展内容
     */
    private String extendJson;

    private String extendJsonFuzzy;

}
