package com.neon.niloweb.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class ObjectMapperConfig
{
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer webJacksonCustomizer()
    {
        return builder -> builder
                // 1. 反序列化时, 忽略JSON中的未知属性
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 2. 序列化空对象时, 不报错
                .featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // 3. LocalDateTime等时间类型输出为字符串, 不输出为数组
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 4. 序列化时, 排除值为 null 的字段, 让 JSON 更简洁
                .serializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder)
    {
        return builder.build();
    }
}
