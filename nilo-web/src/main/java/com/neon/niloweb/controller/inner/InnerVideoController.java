package com.neon.niloweb.controller.inner;

import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "内部-视频接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/video")
@RestController
public class InnerVideoController
{
    private final VideoService videoService;

    @Operation(summary = "获取视频快照")
    @GetMapping("/{videoId}")
    public ResponseVO <VideoSnapshotDTO> getVideoSnapshot(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoService.getVideoSnapshot(videoId));
    }

    @Operation(summary = "增加视频评论数")
    @PostMapping("/{videoId}/comment/count/increase")
    public ResponseVO <Void> increaseCommentCount(@PathVariable(name = "videoId") @NotNull Long videoId,
                                                  @RequestParam(name = "delta", defaultValue = "1") @Min(1) Integer delta)
    {
        videoService.increaseCommentCount(videoId, delta);
        return ResponseVO.success();
    }

    @Operation(summary = "减少视频评论数")
    @PostMapping("/{videoId}/comment/count/decrease")
    public ResponseVO <Void> decreaseCommentCount(@PathVariable(name = "videoId") @NotNull Long videoId,
                                                  @RequestParam(name = "delta", defaultValue = "1") @Min(1) Integer delta)
    {
        videoService.decreaseCommentCount(videoId, delta);
        return ResponseVO.success();
    }
}
