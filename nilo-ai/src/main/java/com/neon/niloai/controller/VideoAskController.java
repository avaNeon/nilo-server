package com.neon.niloai.controller;

import com.neon.niloai.entity.vo.AskQuotaVO;
import com.neon.niloai.repository.redis.WebTokenRedisRepository;
import com.neon.niloai.service.AskQuotaService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "视频问答接口")
@Validated
@RequiredArgsConstructor
@RequestMapping("/ai")
@RestController
public class VideoAskController
{
    private final WebTokenRedisRepository webTokenRedisRepository;

    private final AskQuotaService askQuotaService;

    /**
     * 今天已经问了几次、还剩几次。未登录走统一的未登录响应
     */
    @Operation(summary = "查看今天的提问额度")
    @GetMapping("/ask/quota")
    public ResponseVO <AskQuotaVO> quota(@RequestHeader(name = "token", required = false) String token)
    {
        long userId = webTokenRedisRepository.getUserId(token);
        return ResponseVO.success(askQuotaService.usage(userId));
    }
}
