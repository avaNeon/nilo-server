package com.neon.nilomqconsumer.config;

import com.neon.nilocommon.entity.constants.MqInfo;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.neon.nilocommon.entity.constants.MqInfo.*;

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
                           .deadLetterExchange(MqInfo.DLX_STORAGE_EXCHANGE)        // 绑定死信交换机
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
                           .deadLetterExchange(MqInfo.DLX_STORAGE_EXCHANGE)        // 绑定死信交换机
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
    public DirectExchange dlxStorageExchange()
    {
        return new DirectExchange(MqInfo.DLX_STORAGE_EXCHANGE);
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
        return BindingBuilder.bind(dlqStorageDeleteQueue()).to(dlxStorageExchange()).with(MqInfo.DLQ_STORAGE_DELETE_ROUTING_KEY);
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
        return BindingBuilder.bind(dlqStorageTranscodingQueue())
                             .to(dlxStorageExchange())
                             .with(MqInfo.DLQ_STORAGE_TRANSCODING_ROUTING_KEY);
    }

    // 1. 声明死信交换机和队列
    @Bean
    public DirectExchange dlxVideoHeartbeatExchange()
    {
        return new DirectExchange(DLX_VIDEO_HEARTBEAT_EXCHANGE);
    }

    @Bean
    public Queue videoHeartbeatDlxQueue()
    {
        return new Queue(DLQ_VIDEO_HEARTBEAT_QUEUE, true);
    }

    @Bean
    public Binding videoHeartbeatDlxBinding()
    {
        return BindingBuilder.bind(videoHeartbeatDlxQueue())
                             .to(dlxVideoHeartbeatExchange())
                             .with(DLQ_VIDEO_HEARTBEAT_ROUTING_KEY);
    }

    // 2. 声明业务交换机
    @Bean
    public DirectExchange heartbeatExchange()
    {
        return new DirectExchange(VIDEO_HEARTBEAT_EXCHANGE);
    }

    // 3. 声明业务队列，并绑定死信交换机
    @Bean
    public Queue heartbeatQueue()
    {
        Map <String, Object> args = new HashMap <>();
        // 绑定死信交换机
        args.put("x-dead-letter-exchange", DLX_VIDEO_HEARTBEAT_EXCHANGE);
        args.put("x-dead-letter-routing-key", DLQ_VIDEO_HEARTBEAT_ROUTING_KEY);
        // 消息过期时间 (例如心跳消息超过15秒没被消费，直接丢到死信，因为心跳讲究实时，旧的心跳没用了)
        args.put("x-message-ttl", 15000);
        return new Queue(VIDEO_HEARTBEAT_QUEUE, true, false, false, args);
    }

    // 4. 业务绑定
    @Bean
    public Binding heartbeatBinding()
    {
        return BindingBuilder.bind(heartbeatQueue()).to(heartbeatExchange()).with(VIDEO_HEARTBEAT_ROUTING_KEY);
    }

}
