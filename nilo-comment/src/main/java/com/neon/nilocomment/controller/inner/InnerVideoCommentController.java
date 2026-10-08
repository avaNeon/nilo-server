package com.neon.nilocomment.controller.inner;

import com.neon.nilocomment.service.VideoCommentService;
import com.neon.nilocommon.entity.dto.UserSnapshotDTO;
import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import com.neon.nilocommon.entity.dto.comment.CommentDailyStatisticsDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "内部-视频评论接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/comment")
@RestController
public class InnerVideoCommentController
{
    private final VideoCommentService videoCommentService;

    @Operation(summary = "创作中心评论管理数量")
    @GetMapping("/creator/management/count")
    public ResponseVO <Long> getCreatorCommentManagementInfoCount(@RequestParam(name = "userId") @NotNull Long userId,
                                                                  @RequestParam(name = "videoId", required = false)
                                                                  Long videoId,
                                                                  @RequestParam(name = "nameFuzzy", required = false)
                                                                  String nameFuzzy)
    {
        return ResponseVO.success(videoCommentService.getCreatorCommentManagementInfoCount(userId, videoId, nameFuzzy));
    }

    @Operation(summary = "创作中心评论管理列表")
    @GetMapping("/creator/management/{pageNo}/{pageSize}")
    public ResponseVO <List <CommentManagementVO>> getCreatorCommentManagementInfo(
            @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo,
            @PathVariable(name = "pageSize") @Min(1) @Max(10) @NotNull Integer pageSize,
            @RequestParam(name = "userId") @NotNull Long userId,
            @RequestParam(name = "videoId", required = false) Long videoId,
            @RequestParam(name = "nameFuzzy", required = false) String nameFuzzy)
    {
        return ResponseVO.success(videoCommentService.getCreatorCommentManagementInfo(userId,
                                                                                      videoId,
                                                                                      nameFuzzy,
                                                                                      pageNo,
                                                                                      pageSize));
    }

    @Operation(summary = "归档视频下的评论及评论行为")
    @PostMapping("/archive/{videoId}")
    public ResponseVO <Void> archiveByVideoId(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        videoCommentService.archiveByVideoId(videoId);
        return ResponseVO.success();
    }

    @Operation(summary = "从归档恢复视频下的评论及评论行为")
    @PostMapping("/restore/{videoId}")
    public ResponseVO <Void> restoreByVideoId(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        videoCommentService.restoreByVideoId(videoId);
        return ResponseVO.success();
    }

    @Operation(summary = "清除视频评论归档数据")
    @DeleteMapping("/archive/{videoId}")
    public ResponseVO <Void> purgeArchiveByVideoId(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        videoCommentService.purgeArchiveByVideoId(videoId);
        return ResponseVO.success();
    }

    @Operation(summary = "新增或覆盖视频副本")
    @PutMapping("/replica/video")
    public ResponseVO <Void> upsertVideoReplica(@RequestBody @Valid VideoSnapshotDTO video)
    {
        videoCommentService.upsertVideoReplica(video);
        return ResponseVO.success();
    }

    @Operation(summary = "新增或覆盖用户副本")
    @PutMapping("/replica/user")
    public ResponseVO <Void> upsertUserReplica(@RequestBody @Valid UserSnapshotDTO user)
    {
        videoCommentService.upsertUserReplica(user);
        return ResponseVO.success();
    }

    @Operation(summary = "删除视频副本")
    @DeleteMapping("/replica/video/{videoId}")
    public ResponseVO <Void> deleteVideoReplica(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        videoCommentService.deleteVideoReplica(videoId);
        return ResponseVO.success();
    }

    @Operation(summary = "删除用户副本")
    @DeleteMapping("/replica/user/{userId}")
    public ResponseVO <Void> deleteUserReplica(@PathVariable(name = "userId") @NotNull Long userId)
    {
        videoCommentService.deleteUserReplica(userId);
        return ResponseVO.success();
    }

    @Operation(summary = "查询用户评论获赞总数")
    @GetMapping("/user/{userId}/upvoteCount")
    public ResponseVO <Long> getUpvoteCountByUserId(@PathVariable(name = "userId") @NotNull Long userId)
    {
        return ResponseVO.success(videoCommentService.getUpvoteCountByUserId(userId));
    }

    @Operation(summary = "聚合指定日期各视频作者收到的评论数")
    @GetMapping("/statistics/daily")
    public ResponseVO <List <CommentDailyStatisticsDTO>> getDailyCommentStatistics(
            @RequestParam(name = "statisticsDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull
            LocalDate statisticsDate)
    {
        return ResponseVO.success(videoCommentService.getDailyCommentStatistics(statisticsDate));
    }
}
