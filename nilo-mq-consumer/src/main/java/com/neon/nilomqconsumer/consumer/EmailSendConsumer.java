package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.EmailMessageDTO;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class EmailSendConsumer
{
    private final JavaMailSender javaMailSender;

    private final MailProperties mailProperties;

    private static final String SENDER_NAME = "Nilo";

    /**
     * 邮箱验证码发送消费者
     *
     * @param dto 邮件消息，包含收件邮箱、场景和验证码
     */
    @RabbitListener(queues = MqInfo.EMAIL_SEND_QUEUE)
    public void receiveMessage(EmailMessageDTO dto)
    {
        if (dto == null || dto.getEmail() == null || dto.getScene() == null || dto.getCode() == null)
        {
            throw new IllegalArgumentException("EmailMessageDTO非法");
        }

        try
        {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(mailProperties.getUsername(), SENDER_NAME);
            helper.setTo(dto.getEmail());
            helper.setSubject(buildSubject(dto));
            helper.setText(buildContent(dto), true);
            javaMailSender.send(mimeMessage);
        }
        catch (Exception e)
        {
            throw new RuntimeException("邮件发送失败，email=" + dto.getEmail() + "，scene=" + dto.getScene(), e);
        }
    }

    private String buildSubject(EmailMessageDTO dto)
    {
        return switch (dto.getScene())
        {
            case REGISTER -> "【Nilo】注册验证码";
            case RESET_PASSWORD -> "【Nilo】找回密码验证码";
        };
    }

    private String buildContent(EmailMessageDTO dto)
    {
        String sceneText = switch (dto.getScene())
        {
            case REGISTER -> "注册";
            case RESET_PASSWORD -> "找回密码";
        };
        return """
               <div style="font-family:sans-serif;line-height:1.6;color:#333;">
                   <p>您好，这里是nilo视频网站</p>
                   <p>您正在进行 <b>%s</b> 操作，验证码为：</p>
                   <p style="font-size:28px;font-weight:bold;letter-spacing:4px;color:#1677ff;">%s</p>
                   <p>验证码有效期为5分钟，请勿泄露给他人。如非本人操作，请忽略此邮件。</p>
               </div>
               """.formatted(sceneText, dto.getCode());
    }
}
