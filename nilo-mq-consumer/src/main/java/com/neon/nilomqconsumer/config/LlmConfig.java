package com.neon.nilomqconsumer.config;

import org.springframework.ai.chat.client.ChatClient;
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
     * 字幕质检和翻译用。系统提示词每次调用时传，这里不设默认的
     */
    @Bean
    public ChatClient subtitleChatClient(ChatClient.Builder builder)
    {
        return builder.build();
    }

    /**
     * Spring AI 1.0 没有 spring.ai.openai.read-timeout，读超时要配在 RestClient 上<hr/>
     * 翻译一批台词要等模型把整段 JSON 生成完，给足两分钟。
     * 这个定制对所有 RestClient.Builder 生效，{@link AsrConfig} 里会换成自己的超时设置，不受影响
     */
    @Bean
    public RestClientCustomizer llmRestClientCustomizer()
    {
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS.withReadTimeout(
                Duration.ofMinutes(2))));
    }
}
