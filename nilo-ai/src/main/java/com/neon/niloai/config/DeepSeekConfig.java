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
                                             列表由系统按语义检索得到，相关性可能来自标题、标签或简介；不要因为标题没出现用户原词就说没找到。
                                             只有列表为空，或条目内容确实与问题无关时，才说没有找到。禁止编造 videoId 或标题。
                                             回答时尽量带上对应的 videoId，并可用简介或标签里的原句说明为何相关。
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
