package com.neon.nilomqconsumer.config;

import com.neon.nilocommon.entity.constants.MqInfo;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig
{
    /**
     * 让RabbitMQ使用JSON序列化
     */
    @Bean
    public MessageConverter jsonMessageConverter()
    {
        return new Jackson2JsonMessageConverter();
    }


    /* --储存交换机-- */
    @Bean
    public DirectExchange storageExchange()
    {
        return new DirectExchange(MqInfo.STORAGE_EXCHANGE);
    }

    /* --删除队列-- */
    @Bean
    public Queue storageDeleteQueue()
    {
        return QueueBuilder.durable(MqInfo.STORAGE_DELETE_QUEUE).quorum() // 声明为仲裁队列
                           .deadLetterExchange(MqInfo.DLX_EXCHANGE)        // 绑定死信交换机
                           .deadLetterRoutingKey(MqInfo.DLQ_STORAGE_DELETE_ROUTING_KEY)   // 绑定死信路由键
                           .build();
    }

    @Bean
    public Binding bindingStorageDelete()
    {
        return BindingBuilder.bind(storageDeleteQueue()).to(storageExchange()).with(MqInfo.STORAGE_DELETE_ROUTING_KEY);
    }

    /* --转码队列-- */
    @Bean
    public Queue storageTranscodingQueue()
    {
        return QueueBuilder.durable(MqInfo.STORAGE_TRANSCODING_QUEUE).quorum() // 声明为仲裁队列
                           .deadLetterExchange(MqInfo.DLX_EXCHANGE)        // 绑定死信交换机
                           .deadLetterRoutingKey(MqInfo.DLQ_STORAGE_TRANSCODING_ROUTING_KEY)   // 绑定死信路由键
                           .build();
    }

    @Bean
    public Binding bindingStorageTranscoding()
    {
        return BindingBuilder.bind(storageTranscodingQueue()).to(storageExchange()).with(MqInfo.STORAGE_TRANSCODING_ROUTING_KEY);
    }

    /* --死信交换机-- */
    @Bean
    public DirectExchange dlxExchange()
    {
        return new DirectExchange(MqInfo.DLX_EXCHANGE);
    }

    /* --[死信]删除队列-- */
    @Bean
    public Queue dlqStorageDeleteQueue()
    {
        return QueueBuilder.durable(MqInfo.DLQ_STORAGE_DELETE_QUEUE).quorum() // 死信队列也用仲裁队列保证高可用
                           .build();
    }

    @Bean
    public Binding bindingStorageDeleteDlq()
    {
        return BindingBuilder.bind(dlqStorageDeleteQueue()).to(dlxExchange()).with(MqInfo.DLQ_STORAGE_DELETE_ROUTING_KEY);
    }

    /* --[死信]转码队列-- */
    @Bean
    public Queue dlqStorageTranscodingQueue()
    {
        return QueueBuilder.durable(MqInfo.DLQ_STORAGE_TRANSCODING_QUEUE).quorum() // 死信队列也用仲裁队列保证高可用
                           .build();
    }

    @Bean
    public Binding bindingStorageTranscodingDlq()
    {
        return BindingBuilder.bind(dlqStorageTranscodingQueue()).to(dlxExchange()).with(MqInfo.DLQ_STORAGE_TRANSCODING_ROUTING_KEY);
    }

}
