package com.neon.nilocomment.controller.inner;

import com.neon.nilocomment.service.VideoCommentService;
import com.neon.nilocommon.entity.dto.comment.CommentDailyStatisticsDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    @Operation(summary = "同步视频标题冗余字段")
    @PutMapping("/video/{videoId}/name")
    public ResponseVO <Void> updateVideoNameByVideoId(@PathVariable(name = "videoId") @NotNull Long videoId,
                                                      @RequestParam(name = "videoName") @NotBlank String videoName)
    {
        videoCommentService.updateVideoNameByVideoId(videoId, videoName);
        return ResponseVO.success();
    }

    @Operation(summary = "同步视频封面冗余字段")
    @PutMapping("/video/{videoId}/cover")
    public ResponseVO <Void> updateVideoCoverByVideoId(@PathVariable(name = "videoId") @NotNull Long videoId,
                                                       @RequestParam(name = "videoCover") @NotBlank String videoCover)
    {
        videoCommentService.updateVideoCoverByVideoId(videoId, videoCover);
        return ResponseVO.success();
    }

    @Operation(summary = "同步用户昵称冗余字段")
    @PutMapping("/user/{userId}/nickName")
    public ResponseVO <Void> updateNickNameByUserId(@PathVariable(name = "userId") @NotNull Long userId,
                                                    @RequestParam(name = "nickName") @NotBlank String nickName)
    {
        videoCommentService.updateNickNameByUserId(userId, nickName);
        return ResponseVO.success();
    }

    @Operation(summary = "同步用户头像冗余字段")
    @PutMapping("/user/{userId}/avatar")
    public ResponseVO <Void> updateAvatarByUserId(@PathVariable(name = "userId") @NotNull Long userId,
                                                  @RequestParam(name = "avatar") @NotBlank String avatar)
    {
        videoCommentService.updateAvatarByUserId(userId, avatar);
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
