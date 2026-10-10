package com.neon.nilomqconsumer.config;

import com.neon.nilocommon.entity.constants.MqInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Configuration
public class RabbitMqListenerConfig
{
    @Bean("rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
                                                                               ConnectionFactory connectionFactory)
    {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        // 转码一条要几十分钟，预取的消息在本地排队时也在计时，会被 RabbitMQ 判超时，所以转码队列一次只取一条
        factory.setContainerCustomizer(container ->
                                       {
                                           if (Arrays.asList(container.getQueueNames())
                                                     .contains(MqInfo.STORAGE_TRANSCODING_QUEUE))
                                           {
                                               container.setPrefetchCount(1);
                                           }
                                       });

        factory.setErrorHandler(t ->
                                {
                                    Throwable root = rootCause(t);
                                    if (t instanceof ListenerExecutionFailedException le)
                                    {
                                        MessageProperties messageProperties = le.getFailedMessage().getMessageProperties();
                                        log.error("RabbitMQ Consuming Failed: queue={}, messageId={}, error={}, rootError={}",
                                                  messageProperties.getConsumerQueue(),
                                                  messageProperties.getMessageId(),
                                                  exceptionSummary(le.getCause()),
                                                  exceptionSummary(root));
                                    }
                                    else
                                    {
                                        log.error("RabbitMQ Consuming Failed: error={}", exceptionSummary(root));
                                    }

                                    if (log.isDebugEnabled())
                                    {
                                        log.debug("RabbitMQ Consuming Failed stacktrace", t);
                                    }

                                    // 最后原样抛出，让Spring管理
                                    rethrow(t);
                                });
        return factory;
    }

    /**
     * 原样抛出异常<hr/>
     * <p>不能包一层 RuntimeException 再抛：Spring 的消费循环只认得 ListenerExecutionFailedException 这类异常，
     * 认得的就接着处理下一条消息，认不出来就当作消费者坏了，销毁重建，手上预取的消息也会全部被重投</p>
     *
     * @param t 错误处理器收到的异常
     */
    private void rethrow(Throwable t)
    {
        if (t instanceof RuntimeException runtimeException)
        {
            throw runtimeException;
        }
        else if (t instanceof Error error)
        {
            throw error;
        }
        else
        {
            // 受检异常没法原样抛出，只能包一层
            throw new RuntimeException(t);
        }
    }

    /**
     * 获取异常深层原因
     *
     * @param throwable 表层异常
     * @return 深层异常
     */
    private Throwable rootCause(Throwable throwable)
    {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current)
        {
            current = current.getCause();
        }
        return current == null ? throwable : current;
    }

    /**
     * 总结异常原因
     *
     * @param throwable 异常
     * @return 异常类型和消息的简要描述
     */
    private String exceptionSummary(Throwable throwable)
    {
        if (throwable == null)
        {
            return "unknown";
        }
        String message = Optional.ofNullable(throwable.getMessage()).orElse("no-message");
        return throwable.getClass().getSimpleName() + ": " + message;
    }
}
