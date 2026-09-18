package com.neon.niloai.controller;

import com.neon.niloai.entity.vo.VideoAskVO;
import com.neon.niloai.service.VideoAskService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "视频问答接口")
@Validated
@RequiredArgsConstructor
@RequestMapping("/ai")
@RestController
public class VideoAskController
{
    private final VideoAskService videoAskService;

    /**
     * 检索相关视频并让模型基于检索结果回答
     */
    @Operation(summary = "检索视频并让模型回答")
    @PostMapping("/ask")
    public ResponseVO <VideoAskVO> ask(@RequestParam(name = "question") @NotBlank @Size(max = 100) String question)
    {
        return ResponseVO.success(videoAskService.ask(question));
    }
}
