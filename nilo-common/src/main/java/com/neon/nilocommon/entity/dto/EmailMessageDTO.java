package com.neon.nilocommon.entity.dto;

import com.neon.nilocommon.entity.enums.EmailScene;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮箱验证码发送消息（MQ消息体）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessageDTO
{
    /**
     * 收件邮箱
     */
    private String email;

    /**
     * 场景，用于消费者选择对应的邮件模板
     */
    private EmailScene scene;

    /**
     * 验证码
     */
    private String code;
}
