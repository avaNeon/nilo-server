package com.neon.niloai.config;

import com.neon.niloai.tool.VideoTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把站内视频工具交给 MCP Server<hr/>
 * 外部客户端连上 /sse 后可以调用 searchVideo、getVideoDetail、searchTranscript。
 * 站内问答仍走 {@code videoSearchChatClient}，不经过这里。
 */
@Configuration
public class McpConfig
{
    @Bean
    public ToolCallbackProvider niloTools(VideoTools videoTools)
    {
        return MethodToolCallbackProvider.builder().toolObjects(videoTools).build();
    }
}
