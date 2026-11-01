package com.neon.niloai.controller.inner;

import com.neon.niloai.entity.vo.TranscriptHitVO;
import com.neon.niloai.entity.vo.VideoHitVO;
import com.neon.niloai.tool.VideoTools;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视频检索工具，供问答编排服务的 Agent 调用<hr/>
 * 和 {@link VideoTools} 的三个工具对应。这里不记录引用、不限定视频，这两件事由调用方自己做。
 * 检索词不校验非空：为空时 VideoTools 返回空列表，校验反而会让模型的一次空检索变成整个请求失败
 */
@Tag(name = "内部-视频检索工具接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/video-tool")
@RestController
public class InnerVideoToolController
{
    private final VideoTools videoTools;

    /**
     * 对应工具 searchVideo
     */
    @Operation(summary = "按语义检索视频")
    @GetMapping("/search-video")
    public ResponseVO <List <VideoHitVO>> searchVideo(@RequestParam(name = "query") String query)
    {
        return ResponseVO.success(videoTools.searchVideo(query));
    }

    /**
     * 对应工具 getVideoDetail<hr/>
     * 查不到时 data 为 null
     */
    @Operation(summary = "查询视频详情")
    @GetMapping("/video-detail/{videoId}")
    public ResponseVO <VideoInfoDoc> getVideoDetail(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoTools.findVideo(videoId));
    }

    /**
     * 对应工具 searchTranscript<hr/>
     * 传了 videoId 只搜这个视频，不传搜全站
     */
    @Operation(summary = "按语义检索字幕")
    @GetMapping("/search-transcript")
    public ResponseVO <List <TranscriptHitVO>> searchTranscript(@RequestParam(name = "query") String query,
                                                                @RequestParam(name = "videoId", required = false) Long videoId)
    {
        String scopeVideoId = videoId == null ? null : String.valueOf(videoId);
        return ResponseVO.success(videoTools.searchTranscript(query, scopeVideoId));
    }
}
