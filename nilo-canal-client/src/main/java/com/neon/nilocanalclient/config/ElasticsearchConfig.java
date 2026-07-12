package com.neon.nilocanalclient.config;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.neon.nilocommon.entity.constants.DatePattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * ES 序列化配置。
 * <p>
 * {@code ElasticsearchClient} 走 Jackson，默认不支持 {@link LocalDateTime}；
 * 同时注册与索引 pattern 一致的日期格式，避免写出带 T 的 ISO 字符串。
 */
@Configuration
public class ElasticsearchConfig
{
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DatePattern.DATETIME);

    /**
     * 供 Elasticsearch Java API Client使用的 JsonpMapper<hr/>
     * 在原有的基础上增加了对 {@link LocalDateTime} 的序列化与反序列化支持<hr/>
     */
    @Bean
    public JsonpMapper jsonpMapper()
    {
        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATETIME_FORMATTER));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATETIME_FORMATTER));
        objectMapper.registerModule(javaTimeModule);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new JacksonJsonpMapper(objectMapper);
    }

    /**
     * Spring Data Elasticsearch 读写转换
     */
    @Bean
    public ElasticsearchCustomConversions elasticsearchCustomConversions()
    {
        return new ElasticsearchCustomConversions(Arrays.asList(new LocalDateTimeToStringConverter(),
                                                                new StringToLocalDateTimeConverter()));
    }

    @WritingConverter
    static class LocalDateTimeToStringConverter implements Converter <LocalDateTime, String>
    {
        @Override
        public String convert(LocalDateTime source)
        {
            return source.format(DATETIME_FORMATTER);
        }
    }

    @ReadingConverter
    static class StringToLocalDateTimeConverter implements Converter <String, LocalDateTime>
    {
        @Override
        public LocalDateTime convert(String source)
        {
            return LocalDateTime.parse(source, DATETIME_FORMATTER);
        }
    }
}
