package com.neon.niloai.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.neon.nilocommon.entity.constants.MqInfo.AI_INDEX_DLX;
import static com.neon.nilocommon.entity.constants.MqInfo.AI_INDEX_EXCHANGE;
import static com.neon.nilocommon.entity.constants.MqInfo.AI_SUBTITLE_INDEX_DLK;
import static com.neon.nilocommon.entity.constants.MqInfo.AI_SUBTITLE_INDEX_DLQ;
import static com.neon.nilocommon.entity.constants.MqInfo.AI_SUBTITLE_INDEX_QUEUE;
import static com.neon.nilocommon.entity.constants.MqInfo.AI_SUBTITLE_INDEX_ROUTING_KEY;
import static com.neon.nilocommon.entity.constants.MqInfo.AI_VIDEO_INDEX_DLK;
import static com.neon.nilocommon.entity.constants.MqInfo.AI_VIDEO_INDEX_DLQ;
import static com.neon.nilocommon.entity.constants.MqInfo.AI_VIDEO_INDEX_QUEUE;
import static com.neon.nilocommon.entity.constants.MqInfo.AI_VIDEO_INDEX_ROUTING_KEY;

/**
 * 重建向量索引的队列，消息由 nilo-canal-client 发出<hr/>
 * <p>视频向量跟着 video_info 走（改标题标签简介），字幕块跟着 video_info_file 走（增删换分P）</p>
 */
@Configuration
public class RabbitMqConfig
{
    @Bean
    public MessageConverter jsonMessageConverter()
    {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 消费失败的消息直接进死信队列，不要退回原队列<hr/>
     * Spring 默认失败就重新入队，模型或 ES 挂掉时会变成死循环，一直重试一直失败
     */
    @Bean("rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
                                                                               ConnectionFactory connectionFactory)
    {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public DirectExchange aiIndexExchange()
    {
        return new DirectExchange(AI_INDEX_EXCHANGE);
    }

    @Bean
    public Queue aiVideoIndexQueue()
    {
        return QueueBuilder.durable(AI_VIDEO_INDEX_QUEUE)
                           .quorum()
                           .deadLetterExchange(AI_INDEX_DLX)
                           .deadLetterRoutingKey(AI_VIDEO_INDEX_DLK)
                           .build();
    }

    @Bean
    public Binding aiVideoIndexBinding()
    {
        return BindingBuilder.bind(aiVideoIndexQueue()).to(aiIndexExchange()).with(AI_VIDEO_INDEX_ROUTING_KEY);
    }

    /**
     * 字幕块单独一个队列：重建要读 MinIO 再整片切块灌向量，比标题简介那份慢得多，不该互相堵着
     */
    @Bean
    public Queue aiSubtitleIndexQueue()
    {
        return QueueBuilder.durable(AI_SUBTITLE_INDEX_QUEUE)
                           .quorum()
                           .deadLetterExchange(AI_INDEX_DLX)
                           .deadLetterRoutingKey(AI_SUBTITLE_INDEX_DLK)
                           .build();
    }

    @Bean
    public Binding aiSubtitleIndexBinding()
    {
        return BindingBuilder.bind(aiSubtitleIndexQueue()).to(aiIndexExchange()).with(AI_SUBTITLE_INDEX_ROUTING_KEY);
    }

    @Bean
    public DirectExchange dlxAiIndexExchange()
    {
        return new DirectExchange(AI_INDEX_DLX);
    }

    /**
     * 重建失败的消息进这里，不再自动重试：模型和 ES 都可能一时不可用，攒着人工看
     */
    @Bean
    public Queue dlqAiVideoIndexQueue()
    {
        return QueueBuilder.durable(AI_VIDEO_INDEX_DLQ).quorum().build();
    }

    @Bean
    public Binding dlqAiVideoIndexBinding()
    {
        return BindingBuilder.bind(dlqAiVideoIndexQueue()).to(dlxAiIndexExchange()).with(AI_VIDEO_INDEX_DLK);
    }

    @Bean
    public Queue dlqAiSubtitleIndexQueue()
    {
        return QueueBuilder.durable(AI_SUBTITLE_INDEX_DLQ).quorum().build();
    }

    @Bean
    public Binding dlqAiSubtitleIndexBinding()
    {
        return BindingBuilder.bind(dlqAiSubtitleIndexQueue()).to(dlxAiIndexExchange()).with(AI_SUBTITLE_INDEX_DLK);
    }
}
