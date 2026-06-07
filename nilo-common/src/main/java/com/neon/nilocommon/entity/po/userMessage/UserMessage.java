package com.neon.nilocommon.entity.po.userMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;


/**
 * 用户消息表
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserMessage
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
    private LocalDate createTime;

    /**
     * 扩展内容
     */
    private String extendJson;
}
