package com.neon.niloai.interceptor;

import com.neon.niloai.service.McpQuotaService;
import com.neon.niloai.service.McpTokenService;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MCP 连接鉴权 + 每日调用额度拦截器<hr/>
 * 拦 /sse 和 /mcp/message：先校验 token 查出 userId，
 * 如果是 /mcp/message（真正调用工具）就顺便扣一次今天的额度，不区分调了哪个工具
 */
@RequiredArgsConstructor
@Component
public class McpAuthInterceptor implements HandlerInterceptor
{
    // 不用 "token" 作为请求头名，避免跟网站登录 token 名字撞车（本模块 /ai/ask 等接口的 "token" 都是指登录 token）
    private static final String TOKEN_HEADER = "X-Mcp-Token";

    private final McpTokenService mcpTokenService;

    private final McpQuotaService mcpQuotaService;

    @Value("${spring.ai.mcp.server.sse-message-endpoint}")
    private String sseMessageEndpoint;

    @Override
    public boolean preHandle(HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler)
    {
        // token 放在请求头里，不是查询参数——查询参数只属于这一个 URL，
        // 而 SDK 后面会把客户端引到另一个带 sessionId 的 URL 上，查询参数带不过去，请求头才会一直带着
        String token = request.getHeader(TOKEN_HEADER);

        // 查不到对应用户，说明 token 有问题，拒绝
        Long userId = mcpTokenService.getUserId(token);
        if (userId == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }

        // 只有真正调用（/mcp/message）才扣额度，建连（/sse）本身不算一次调用
        if (request.getRequestURI().equals(sseMessageEndpoint))
        {
            mcpQuotaService.consume(userId);
        }

        return true;
    }
}
