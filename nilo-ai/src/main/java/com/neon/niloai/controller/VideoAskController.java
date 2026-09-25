package com.neon.niloai.controller;

import com.neon.niloai.entity.vo.AskQuotaVO;
import com.neon.niloai.repository.redis.WebTokenRedisRepository;
import com.neon.niloai.service.AskQuotaService;
import com.neon.niloai.service.VideoAskStreamService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "视频问答接口")
@Validated
@RequiredArgsConstructor
@RequestMapping("/ai")
@RestController
public class VideoAskController
{
    private final VideoAskStreamService videoAskStreamService;

    private final WebTokenRedisRepository webTokenRedisRepository;

    private final AskQuotaService askQuotaService;

    /**
     * 检索相关视频并让模型基于检索结果回答，同一个 conversationId 内支持多轮追问<hr/>
     * 先推进度，核对完时间点后再推正文，最后给视频和片段。
     * 登录和每日次数在流里面检查，没通过时用 error 事件把原因推回去，浏览器按 SSE 读就行
     *
     * @param conversationId 前端生成，只允许字母、数字和短横线，因为它会直接拼进 Redis key
     * @param videoId        视频页提问时带上当前视频，字幕只搜这个视频；首页不传
     */
    @Operation(summary = "检索视频并让模型回答")
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestHeader(name = "token", required = false) String token,
                          @RequestParam(name = "question") @NotBlank @Size(max = 100) String question,
                          @RequestParam(name = "conversationId") @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,64}")
                          String conversationId,
                          @RequestParam(name = "videoId", required = false) Long videoId,
                          HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return videoAskStreamService.open(token, question, conversationId, videoId);
    }

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
