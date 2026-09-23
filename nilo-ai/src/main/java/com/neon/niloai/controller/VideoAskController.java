package com.neon.niloai.controller;

import com.neon.niloai.service.VideoAskStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "视频问答接口")
@Validated
@RequiredArgsConstructor
@RequestMapping("/ai")
@RestController
public class VideoAskController
{
    private final VideoAskStreamService videoAskStreamService;

    /**
     * 检索相关视频并让模型基于检索结果回答，同一个 conversationId 内支持多轮追问<hr/>
     * 先推进度，核对完时间点后再推正文，最后给视频和片段
     *
     * @param conversationId 前端生成，只允许字母、数字和短横线，因为它会直接拼进 Redis key
     * @param videoId        视频页提问时带上当前视频，字幕只搜这个视频；首页不传
     */
    @Operation(summary = "检索视频并让模型回答")
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestParam(name = "question") @NotBlank @Size(max = 100) String question,
                          @RequestParam(name = "conversationId") @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,64}")
                          String conversationId,
                          @RequestParam(name = "videoId", required = false) Long videoId,
                          HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return videoAskStreamService.open(question, conversationId, videoId);
    }
}
