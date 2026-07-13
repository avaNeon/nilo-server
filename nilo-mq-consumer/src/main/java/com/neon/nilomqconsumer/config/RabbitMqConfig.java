package com.neon.nilomqconsumer.config;

import com.neon.nilocommon.entity.constants.MqInfo;
import org.springframework.amqp.core.*;
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
        return QueueBuilder.durable(MqInfo.STORAGE_VIDEO_DELETE_QUEUE).quorum() // 声明为仲裁队列
                           .deadLetterExchange(MqInfo.STORAGE_DLX)        // 绑定死信交换机
                           .deadLetterRoutingKey(MqInfo.STORAGE_VIDEO_DELETE_DLK)   // 绑定死信路由键
                           .build();
    }

    @Bean
    public Binding bindingStorageDelete()
    {
        return BindingBuilder.bind(storageDeleteQueue()).to(storageExchange()).with(MqInfo.STORAGE_VIDEO_DELETE_ROUTING_KEY);
    }

    /* --图片删除队列-- */
    @Bean
    public Queue storageImageDeleteQueue()
    {
        return QueueBuilder.durable(MqInfo.STORAGE_IMAGE_DELETE_QUEUE)
                           .quorum()
                           .deadLetterExchange(MqInfo.STORAGE_DLX)
                           .deadLetterRoutingKey(MqInfo.STORAGE_IMAGE_DELETE_DLK)
                           .build();
    }

    @Bean
    public Binding bindingStorageImageDelete()
    {
        return BindingBuilder.bind(storageImageDeleteQueue()).to(storageExchange()).with(MqInfo.STORAGE_IMAGE_DELETE_ROUTING_KEY);
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
        return QueueBuilder.durable(MqInfo.STORAGE_VIDEO_DELETE_DLQ).quorum() // 死信队列也用仲裁队列保证高可用
                           .build();
    }

    @Bean
    public Binding bindingStorageDeleteDlq()
    {
        return BindingBuilder.bind(dlqStorageDeleteQueue()).to(dlxStorageExchange()).with(MqInfo.STORAGE_VIDEO_DELETE_DLK);
    }

    /* --[死信]图片删除队列-- */
    @Bean
    public Queue dlqStorageImageDeleteQueue()
    {
        return QueueBuilder.durable(MqInfo.STORAGE_IMAGE_DELETE_DLQ).quorum().build();
    }

    @Bean
    public Binding bindingStorageImageDeleteDlq()
    {
        return BindingBuilder.bind(dlqStorageImageDeleteQueue()).to(dlxStorageExchange()).with(MqInfo.STORAGE_IMAGE_DELETE_DLK);
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
        return BindingBuilder.bind(dlqStorageTranscodingQueue()).to(dlxStorageExchange()).with(MqInfo.STORAGE_TRANSCODING_DLK);
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
        return QueueBuilder.durable(VIDEO_HEARTBEAT_QUEUE).ttl(15000).build();
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
        return QueueBuilder.durable(STATISTIC_DLQ).quorum().build();
    }

    /* 统计死信队列-死信交换机绑定 */
    @Bean
    public Binding statisticsDlqBinding()
    {
        return BindingBuilder.bind(dlqStatisticsQueue()).to(dlxStatisticsExchange()).with(STATISTIC_DLK);
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
        return QueueBuilder.durable(PLAY_COUNT_QUEUE).quorum().build();
    }

    /* 播放量刷新队列-交换机绑定 */
    @Bean
    public Binding playCountBinding()
    {
        return BindingBuilder.bind(playCountQueue()).to(playCountExchange()).with(PLAY_COUNT_ROUTING_KEY);
    }


    /* 邮件交换机 */
    @Bean
    public DirectExchange emailExchange()
    {
        return new DirectExchange(MqInfo.EMAIL_EXCHANGE);
    }

    /* 邮箱验证码发送队列 */
    @Bean
    public Queue emailSendQueue()
    {
        return QueueBuilder.durable(MqInfo.EMAIL_SEND_QUEUE)
                           .quorum()
                           .deadLetterExchange(MqInfo.EMAIL_DLX)
                           .deadLetterRoutingKey(MqInfo.EMAIL_SEND_DLK)
                           .build();
    }

    /* 邮箱验证码发送队列-交换机绑定 */
    @Bean
    public Binding emailSendBinding()
    {
        return BindingBuilder.bind(emailSendQueue()).to(emailExchange()).with(MqInfo.EMAIL_SEND_ROUTING_KEY);
    }

    /* 邮件死信交换机 */
    @Bean
    public DirectExchange dlxEmailExchange()
    {
        return new DirectExchange(MqInfo.EMAIL_DLX);
    }

    /* 邮箱验证码发送死信队列 */
    @Bean
    public Queue dlqEmailSendQueue()
    {
        return QueueBuilder.durable(MqInfo.EMAIL_SEND_DLQ).quorum().build();
    }

    /* 邮箱验证码发送死信队列-死信交换机绑定 */
    @Bean
    public Binding emailSendDlqBinding()
    {
        return BindingBuilder.bind(dlqEmailSendQueue()).to(dlxEmailExchange()).with(MqInfo.EMAIL_SEND_DLK);
    }

}


