package com.neon.nilomqconsumer.config;

import com.neon.nilocommon.entity.constants.MqInfo;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.neon.nilocommon.entity.constants.MqInfo.*;

@Configuration
public class RabbitMqConfig
{
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
                           .deadLetterExchange(MqInfo.STORAGE_DLX)        // 绑定死信交换机
                           .deadLetterRoutingKey(MqInfo.STORAGE_DELETE_DLK)   // 绑定死信路由键
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
                           .deadLetterExchange(MqInfo.STORAGE_DLX)        // 绑定死信交换机
                           .deadLetterRoutingKey(MqInfo.STORAGE_TRANSCODING_DLK)   // 绑定死信路由键
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
        return new DirectExchange(MqInfo.STORAGE_DLX);
    }

    /* --[死信]删除队列-- */
    @Bean
    public Queue dlqStorageDeleteQueue()
    {
        return QueueBuilder.durable(MqInfo.STORAGE_DELETE_DLQ).quorum() // 死信队列也用仲裁队列保证高可用
                           .build();
    }

    @Bean
    public Binding bindingStorageDeleteDlq()
    {
        return BindingBuilder.bind(dlqStorageDeleteQueue()).to(dlxStorageExchange()).with(MqInfo.STORAGE_DELETE_DLK);
    }

    /* --[死信]转码队列-- */
    @Bean
    public Queue dlqStorageTranscodingQueue()
    {
        return QueueBuilder.durable(MqInfo.STORAGE_TRANSCODING_DLQ).quorum() // 死信队列也用仲裁队列保证高可用
                           .build();
    }

    @Bean
    public Binding bindingStorageTranscodingDlq()
    {
        return BindingBuilder.bind(dlqStorageTranscodingQueue())
                             .to(dlxStorageExchange())
                             .with(MqInfo.STORAGE_TRANSCODING_DLK);
    }

    /* 心跳交换机 */
    @Bean
    public DirectExchange heartbeatExchange()
    {
        return new DirectExchange(VIDEO_HEARTBEAT_EXCHANGE);
    }

    /* 心跳队列 */
    @Bean
    public Queue heartbeatQueue()
    {
        return QueueBuilder.durable(VIDEO_HEARTBEAT_QUEUE)
                           .ttl(15000)
                           .build();
    }

    /* 队列-交换机绑定 */
    @Bean
    public Binding heartbeatBinding()
    {
        return BindingBuilder.bind(heartbeatQueue()).to(heartbeatExchange()).with(VIDEO_HEARTBEAT_ROUTING_KEY);
    }

    /* 统计交换机 */
    @Bean
    public DirectExchange statisticsExchange()
    {
        return new DirectExchange(STATISTIC_EXCHANGE);
    }

    /* 统计队列 */
    @Bean
    public Queue statisticsQueue()
    {
        return QueueBuilder.durable(STATISTIC_QUEUE)
                           .quorum()
                           .deadLetterExchange(STATISTIC_DLX)
                           .deadLetterRoutingKey(STATISTIC_DLK)
                           .build();
    }

    /* 统计队列-交换机绑定 */
    @Bean
    public Binding statisticsBinding()
    {
        return BindingBuilder.bind(statisticsQueue()).to(statisticsExchange()).with(STATISTIC_ROUTING_KEY);
    }

    /* 统计死信交换机 */
    @Bean
    public DirectExchange dlxStatisticsExchange()
    {
        return new DirectExchange(STATISTIC_DLX);
    }

    /* 统计死信队列 */
    @Bean
    public Queue dlqStatisticsQueue()
    {
        return QueueBuilder.durable(STATISTIC_DLQ)
                           .quorum()
                           .build();
    }

    /* 统计死信队列-死信交换机绑定 */
    @Bean
    public Binding statisticsDlqBinding()
    {
        return BindingBuilder.bind(dlqStatisticsQueue())
                             .to(dlxStatisticsExchange())
                             .with(STATISTIC_DLK);
    }

    /* 播放量刷新交换机 */
    @Bean
    public DirectExchange playCountExchange()
    {
        return new DirectExchange(PLAY_COUNT_EXCHANGE);
    }

    /* 播放量刷新队列 */
    @Bean
    public Queue playCountQueue()
    {
        return QueueBuilder.durable(PLAY_COUNT_QUEUE)
                           .quorum()
                           .build();
    }

    /* 播放量刷新队列-交换机绑定 */
    @Bean
    public Binding playCountBinding()
    {
        return BindingBuilder.bind(playCountQueue()).to(playCountExchange()).with(PLAY_COUNT_ROUTING_KEY);
    }

}
