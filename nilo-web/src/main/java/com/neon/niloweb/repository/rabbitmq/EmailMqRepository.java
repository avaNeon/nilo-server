package com.neon.niloweb.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.EmailMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class EmailMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送邮箱验证码消息，由nilo-mq-consumer异步发信
     *
     * @param dto 邮件消息
     */
    public void sendEmailCode(EmailMessageDTO dto)
    {
        rabbitTemplate.convertAndSend(MqInfo.EMAIL_EXCHANGE, MqInfo.EMAIL_SEND_ROUTING_KEY, dto);
    }
}
