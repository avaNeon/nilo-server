package com.neon.nilocommon.autoconfigure.serialization;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一序列化规则
 */
@RequiredArgsConstructor
@EnableConfigurationProperties(GlobalSerializationProperties.class)
@ConditionalOnProperty(prefix = "common.global.serialization", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfiguration
public class GlobalSerializationAutoConfiguration
{
    private final GlobalSerializationProperties properties;

    /**
     * 这样可以和其它Jackson2ObjectMapperBuilderCustomizer叠加
     *
     * @return 全局Jackson2ObjectMapperBuilderCustomizer
     */
    @Bean
    @Order(1) //SpringBoot默认的配置是0，这里我们给个1把默认配置覆盖掉吧
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer()
    {
        return builder ->
        {
            //针对于LocalDateTime类的序列化反序列化规则
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(properties.pattern)));
            builder.deserializerByType(LocalDateTime.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(properties.pattern)));
        };
    }
}
