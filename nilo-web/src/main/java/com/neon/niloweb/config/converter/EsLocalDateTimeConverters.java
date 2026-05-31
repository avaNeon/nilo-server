package com.neon.niloweb.config.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ES日期格式转化器<hr/>
 * Java中LocalDateTime默认的日期格式中间带T，让我们把它转化为 "yyyy-MM-dd HH:mm:ss" 格式
 */
public class EsLocalDateTimeConverters
{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Java -> Elasticsearch (序列化写出)
    @WritingConverter
    public static class LocalDateTimeToStringConverter implements Converter <LocalDateTime, String>
    {
        @Override
        public String convert(LocalDateTime source)
        {
            return source.format(FORMATTER);
        }
    }

    // Elasticsearch -> Java (反序列化读取)
    @ReadingConverter
    public static class StringToLocalDateTimeConverter implements Converter <String, LocalDateTime>
    {
        @Override
        public LocalDateTime convert(String source)
        {
            return LocalDateTime.parse(source, FORMATTER);
        }
    }
}
