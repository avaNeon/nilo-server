package com.neon.nilocommon.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <b>消息统计</b><hr/>
 * <p>里面包含了{@link com.neon.nilocommon.entity.enums.userMessage.MessageType}中的四种消息的统计</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserMessageCount
{
    private Integer systemMessageCount;

    private Integer likeMessageCount;

    private Integer collectMessageCount;

    private Integer commentMessageCount;
}
