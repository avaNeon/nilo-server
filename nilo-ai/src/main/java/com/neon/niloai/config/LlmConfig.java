package com.neon.niloai.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig
{
    /**
     * 工具返回的 JSON 统一把 Long 写成字符串。Spring AI 1.0 的结果转换写死走 {@link JsonParser}，没有可替换的 Bean。
     */
    @Bean
    public InitializingBean toolResultLongAsString()
    {
        return () ->
        {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            JsonParser.getObjectMapper().registerModule(module);
        };
    }

    /**
     * Spring AI 1.0 没有 spring.ai.openai.read-timeout，读超时要配在 RestClient 上
     */
    @Bean
    public RestClientCustomizer llmRestClientCustomizer()
    {
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS.withReadTimeout(
                Duration.ofSeconds(90))));
    }
}
