package com.neon.nilocommon.entity.dto;

import com.neon.nilocommon.entity.po.userMessage.ExtendJson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserMessageDTO
{
    /**
     * 消息ID
     */
    private Long messageId;

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
    private ExtendJson extendJson;

    /**
     * 发送者用户昵称
     */
    private String nickName;

    /**
     * 发送者用户头像
     */
    private String avatar;

    /**
     * 视频封面
     */
    private String videoCover;

    /**
     * 视频名称
     */
    private String videoName;
}
