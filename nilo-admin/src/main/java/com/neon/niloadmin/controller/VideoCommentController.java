package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.VideoCommentService;
import com.neon.nilocommon.entity.po.VideoComment;
import com.neon.nilocommon.entity.query.VideoCommentQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "视频评论管理")
@RequiredArgsConstructor
@Validated
@RequestMapping(path = "/comment")
@RestController
public class VideoCommentController
{
    private final VideoCommentService videoCommentService;

    @Operation(summary = "获取视频评论列表")
    @PostMapping(path = "/list")
    public ResponseVO <List <VideoComment>> loadCommentList(@RequestBody VideoCommentQuery query)
    {
        return ResponseVO.success(videoCommentService.loadCommentList(query));
    }

    @Operation(summary = "删除视频评论")
    @DeleteMapping(path = "/{commentId}")
    public ResponseVO <Object> deleteComment(@PathVariable(name = "commentId") @NotNull Long commentId)
    {
        videoCommentService.deleteComment(commentId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "真正删除指定视频评论", description = "仅会删除已逻辑删除的评论")
    @DeleteMapping(path = "/destroy/{commentId}")
    public ResponseVO <Object> destroyComment(@PathVariable(name = "commentId") @NotNull Long commentId)
    {
        videoCommentService.destroyComment(commentId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "真正删除指定时间范围的视频评论", description = "仅会删除已逻辑删除的评论，时间格式：yyyy-MM-dd")
    @DeleteMapping(path = "/destroy/range")
    public ResponseVO <Object> destroyCommentsByPostTimeRange(
            @RequestParam(name = "postTimeStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate postTimeStart,
            @RequestParam(name = "postTimeEnd") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate postTimeEnd)
    {
        videoCommentService.destroyCommentsByPostTimeRange(postTimeStart, postTimeEnd);
        return ResponseVO.success(null);
    }
}
