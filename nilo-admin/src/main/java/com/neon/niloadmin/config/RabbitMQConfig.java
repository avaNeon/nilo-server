package com.neon.niloadmin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig
{
    /**
     * 让RabbitMQ使用JSON序列化
     */
    @Bean
    public MessageConverter jsonMessageConverter()
    {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 消息没进 broker、或者进了 broker 却没路由进队列时留个日志<hr/>
     * <p>不挂这两个回调的话这两种失败都是静默的，发送方一无所知。</p>
     * <p>confirm 是异步回调，这里只记录不补发：真丢了看日志人工补。</p>
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer()
    {
        return rabbitTemplate ->
        {
            rabbitTemplate.setConfirmCallback((correlationData, ack, cause) ->
                                              {
                                                  if (!ack)
                                                  {
                                                      log.error("消息未被 broker 确认, correlationData={}, cause={}", correlationData, cause);
                                                  }
                                              });
            rabbitTemplate.setReturnsCallback(returned -> log.error(
                    "消息没能路由进队列, exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText()));
        };
    }
}
