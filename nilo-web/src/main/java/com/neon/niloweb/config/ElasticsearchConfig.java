package com.neon.niloweb.config;

import com.neon.niloweb.config.converter.EsLocalDateTimeConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;

import java.util.Arrays;

@Configuration
public class ElasticsearchConfig
{
    /**
     * 配置不同类型属性转化方式
     */
    @Bean
    public ElasticsearchCustomConversions elasticsearchCustomConversions()
    {
        return new ElasticsearchCustomConversions(Arrays.asList(new EsLocalDateTimeConverters.LocalDateTimeToStringConverter(),
                                                                new EsLocalDateTimeConverters.StringToLocalDateTimeConverter()));
    }
}