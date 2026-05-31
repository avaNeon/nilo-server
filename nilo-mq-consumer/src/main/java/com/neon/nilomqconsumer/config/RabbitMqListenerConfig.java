package com.neon.nilomqconsumer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                                        if (log.isDebugEnabled())
                                        {
                                            log.debug("RabbitMQ Consuming Failed stacktrace", t);
                                        }

                                        // 最后抛出异常，让Spring管理
                                        try
                                        {
                                            throw t;
                                        }
                                        catch (Throwable e)
                                        {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    log.error("RabbitMQ Consuming Failed: error={}", exceptionSummary(root));

                                    if (log.isDebugEnabled())
                                    {
                                        log.debug("RabbitMQ Consuming Failed stacktrace", t);
                                    }

                                    // 最后抛出异常，让Spring管理
                                    try
                                    {
                                        throw t;
                                    }
                                    catch (Throwable e)
                                    {
                                        throw new RuntimeException(e);
                                    }
                                });
        return factory;
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
