package com.neon.niloadmin.config;

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
    public Jackson2ObjectMapperBuilderCustomizer adminJacksonCustomizer()
    {
        return builder -> builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                 .featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                 .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                 .serializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder)
    {
        return builder.build();
    }
}
