package com.neon.niloai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class DeepSeekConfig
{
    /**
     * 对接 DeepSeek 的 ChatClient，系统提示词约束模型只能依据检索列表回答
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder)
    {
        return builder.defaultSystem("""
                                             你是本站（Nilo）的视频助手。只能根据用户消息里提供的检索视频列表回答。
                                             如果列表为空或没有相关视频，明确说没有找到，禁止编造 videoId 或标题。
                                             回答时尽量带上对应的 videoId，方便用户打开视频。
                                             """).build();
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
