package com.neon.niloai.controller;

import com.neon.niloai.repository.redis.WebTokenRedisRepository;
import com.neon.niloai.service.McpTokenService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "MCP token 管理")
@Validated
@RequiredArgsConstructor
@RequestMapping("/ai/mcp/token")
@RestController
public class McpTokenController
{
    private final WebTokenRedisRepository webTokenRedisRepository;

    private final McpTokenService mcpTokenService;

    /**
     * 生成/重新生成 MCP token<hr/>
     * 需要先用网站账号登录，带上登录 token 来换。重复调用会顶掉上一个 MCP token，
     * 旧的 token 立刻失效
     */
    @Operation(summary = "生成 MCP token", description = "需要网站登录 token，返回的 token 用于连接 MCP，跟登录 token 是两套凭证")
    @PostMapping
    public ResponseVO <String> issue(@RequestHeader(name = "token") @NotBlank String token)
    {
        long userId = webTokenRedisRepository.getUserId(token);
        return ResponseVO.success(mcpTokenService.issue(userId));
    }

    /**
     * 撤销当前生效的 MCP token，不影响网站登录态
     */
    @Operation(summary = "撤销 MCP token", description = "需要网站登录 token；撤销后原来的 MCP token 立刻失效")
    @DeleteMapping
    public ResponseVO <Void> revoke(@RequestHeader(name = "token") @NotBlank String token)
    {
        long userId = webTokenRedisRepository.getUserId(token);
        mcpTokenService.revoke(userId);
        return ResponseVO.success();
    }
}
