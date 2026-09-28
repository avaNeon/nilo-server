package com.neon.niloai.service;

import com.neon.niloai.repository.redis.McpTokenRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * MCP 专用 token 的生成与校验<hr/>
 * 用户先用网站账号登录拿到登录 token，再拿登录 token 换一个这里发的 MCP token，
 * 配进 MCP 客户端后长期使用，跟网站登录态互不影响
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class McpTokenService
{
    /**
     * MCP token 有效期，比网站登录 token 长很多，减少用户重新生成的频率
     */
    private static final int EXPIRE_DAYS = 90;

    /**
     * 加个前缀方便一眼认出这是哪种 token，泄露排查时也好检索
     */
    private static final String TOKEN_PREFIX = "mcp_";

    private final McpTokenRedisRepository mcpTokenRedisRepository;

    /**
     * 给这个用户发一个新的 MCP token，会顶掉这个用户之前发过的旧 token
     */
    public String issue(long userId)
    {
        String newToken = TOKEN_PREFIX + UUID.randomUUID();
        mcpTokenRedisRepository.issue(userId, newToken, EXPIRE_DAYS);
        log.info("[MCP token] 发放新 token, userId={}", userId);
        return newToken;
    }

    /**
     * 撤销这个用户当前生效的 MCP token
     */
    public void revoke(long userId)
    {
        mcpTokenRedisRepository.revoke(userId);
        log.info("[MCP token] 撤销 token, userId={}", userId);
    }

    /**
     * 通过 MCP token 取出对应的 userId，查不到或过期返回 null
     */
    public Long getUserId(String token)
    {
        return mcpTokenRedisRepository.getUserId(token);
    }
}
