package com.neon.niloai.controller.inner;

import com.neon.niloai.entity.vo.VideoFileSummary;
import com.neon.niloai.service.VideoInfoService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 视频查询，供问答编排服务调用
 */
@Tag(name = "内部-视频接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/video")
@RestController
public class InnerVideoController
{
    private final VideoInfoService videoInfoService;

    /**
     * 视频页每轮提问都用它确认 videoId 有效
     */
    @Operation(summary = "查询视频标题")
    @GetMapping("/{videoId}")
    public ResponseVO <String> findVideoName(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoInfoService.findVideoName(videoId));
    }

    /**
     * 视频页新会话初始化时用它取各分P的总结，作为对话背景
     */
    @Operation(summary = "查询视频各分P的总结")
    @GetMapping("/{videoId}/summaries")
    public ResponseVO <List <VideoFileSummary>> listSummaries(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoInfoService.listSummaries(videoId));
    }
}
