package com.neon.niloai.config;

import com.neon.niloai.interceptor.McpAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器配置器
 */
@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer
{
    private final McpAuthInterceptor mcpAuthInterceptor;

    @Value("${spring.ai.mcp.server.sse-endpoint}")
    private String sseEndpoint;

    @Value("${spring.ai.mcp.server.sse-message-endpoint}")
    private String sseMessageEndpoint;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        // 只拦 MCP 的两个入口（建连 /sse、调工具 /mcp/message）
        registry.addInterceptor(mcpAuthInterceptor).addPathPatterns(sseEndpoint, sseMessageEndpoint);
    }
}
