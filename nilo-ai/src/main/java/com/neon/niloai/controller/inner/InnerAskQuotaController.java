package com.neon.niloai.controller.inner;

import com.neon.niloai.repository.redis.WebTokenRedisRepository;
import com.neon.niloai.service.AskQuotaService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 提问额度的扣减和归还，供问答编排服务在提问前后调用<hr/>
 * 登录校验放在扣减里一起做：调用方只需要拿到归还用的 key，不需要知道 userId
 */
@Tag(name = "内部-提问额度接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/ask-quota")
@RestController
public class InnerAskQuotaController
{
    private final WebTokenRedisRepository webTokenRedisRepository;

    private final AskQuotaService askQuotaService;

    /**
     * 校验登录并扣掉今天的一次。未登录或次数用完时返回业务错误
     *
     * @param token 用户的网站登录 token，由调用方从前端请求里原样转过来。未登录时前端传空串，
     *              这里不做非空校验，交给 getUserId 返回「未登录」，否则调用方只能拿到「请求参数不合法」
     * @return 这次扣减用的 key，提问失败时拿它归还
     */
    @Operation(summary = "校验登录并扣减一次提问额度")
    @PostMapping("/consume")
    public ResponseVO <String> consume(@RequestHeader(name = "token", required = false) String token)
    {
        long userId = webTokenRedisRepository.getUserId(token);
        return ResponseVO.success(askQuotaService.consume(userId));
    }

    /**
     * 服务端出错时归还这一次。归还失败只记日志，同样返回成功
     */
    @Operation(summary = "归还一次提问额度")
    @PostMapping("/refund")
    public ResponseVO <Void> refund(@RequestParam(name = "quotaKey") @NotBlank String quotaKey)
    {
        askQuotaService.refund(quotaKey);
        return ResponseVO.success();
    }
}
