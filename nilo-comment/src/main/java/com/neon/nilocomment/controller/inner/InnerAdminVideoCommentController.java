package com.neon.nilocomment.controller.inner;

import com.neon.nilocomment.service.VideoCommentService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "内部-Admin视频评论接口", description = "仅供 admin 服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/comment/admin")
@RestController
public class InnerAdminVideoCommentController
{
    private final VideoCommentService videoCommentService;

    @Operation(summary = "Admin评论管理数量")
    @GetMapping("/management/count")
    public ResponseVO <Long> getAdminCommentManagementInfoCount(
            @RequestParam(name = "nameFuzzy", required = false) String nameFuzzy)
    {
        return ResponseVO.success(videoCommentService.getAdminCommentManagementInfoCount(nameFuzzy));
    }

    @Operation(summary = "Admin评论管理列表")
    @GetMapping("/management/{pageNo}/{pageSize}")
    public ResponseVO <List <CommentManagementAdmin>> getAdminCommentManagementInfo(
            @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo,
            @PathVariable(name = "pageSize") @Min(1) @Max(10) @NotNull Integer pageSize,
            @RequestParam(name = "nameFuzzy", required = false) String nameFuzzy)
    {
        return ResponseVO.success(videoCommentService.getAdminCommentManagementInfo(nameFuzzy, pageNo, pageSize));
    }

    @Operation(summary = "Admin逻辑删除评论")
    @DeleteMapping("/{commentId}")
    public ResponseVO <Void> deleteCommentByAdmin(@PathVariable(name = "commentId") @NotNull Long commentId)
    {
        videoCommentService.deleteCommentByAdmin(commentId);
        return ResponseVO.success();
    }

    @Operation(summary = "真正删除指定已逻辑删除评论")
    @DeleteMapping("/destroy/{commentId}")
    public ResponseVO <Void> destroyComment(@PathVariable(name = "commentId") @NotNull Long commentId)
    {
        videoCommentService.destroyComment(commentId);
        return ResponseVO.success();
    }

    @Operation(summary = "真正删除指定时间范围的已逻辑删除评论")
    @DeleteMapping("/destroy/range")
    public ResponseVO <Void> destroyCommentsByPostTimeRange(
            @RequestParam(name = "postTimeStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull
            LocalDate postTimeStart,
            @RequestParam(name = "postTimeEnd") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull
            LocalDate postTimeEnd)
    {
        videoCommentService.destroyCommentsByPostTimeRange(postTimeStart, postTimeEnd);
        return ResponseVO.success();
    }
}
