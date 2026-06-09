package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.VideoService;
import com.neon.nilocommon.entity.dto.VideoInfoUploadAdminJoinDTO;
import com.neon.nilocommon.entity.dto.VideoInfoUploadAdminQueryDTO;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.query.VideoInfoUploadQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "视频管理")
@RequiredArgsConstructor
@Validated
@RequestMapping(path = "/video")
@RestController
public class VideoController
{
    /* 自动装配 */

    private final VideoService videoService;

    /**
     * 查询用户上传视频
     *
     * @return 查询结果（视频列表）
     */
    @Operation(summary = "获取视频列表")
    @PostMapping(path = "/list")
    public ResponseVO <List <VideoInfoUploadAdminJoinDTO>> loadVideoList(
            @RequestBody @Validated VideoInfoUploadAdminQueryDTO infoUploadQueryDTO)
    {
        VideoInfoUploadQuery infoUploadQuery = new VideoInfoUploadQuery();
        BeanUtils.copyProperties(infoUploadQueryDTO, infoUploadQuery);
        if (infoUploadQuery.getPageSize() == null)
        {
            infoUploadQuery.setPageSize(10);
        }
        List <VideoInfoUploadAdminJoinDTO> result = videoService.loadVideoList(infoUploadQuery);
        return ResponseVO.success(result);
    }

    @Operation(summary = "获取视频分P列表")
    @GetMapping(path = "/file/{videoId}")
    public ResponseVO <List <VideoInfoFileUpload>> loadVideoFileList(@PathVariable(name = "videoId") Long videoId)
    {
        return ResponseVO.success(videoService.loadVideoFileList(videoId));
    }

    @Operation(summary = "审核视频")
    @Parameters({@Parameter(name = "videoId", description = "视频ID"),
                 @Parameter(name = "reviewResult", description = "审核结果，true表示审核通过，false表示审核不通过"),
                 @Parameter(name = "refuseReason", description = "拒绝理由，当审核不通过时需要提供")})
    @PutMapping(path = "/review")
    public ResponseVO <Object> reviewVideo(@RequestParam(name = "videoId") @NotNull Long videoId,
                                           @RequestParam(name = "reviewResult") @NotNull Boolean reviewResult,
                                           @RequestParam(name = "refuseReason", required = false) String refuseReason)
    {
        videoService.reviewVideo(videoId, reviewResult, refuseReason);
        return ResponseVO.success(null);
    }

    /**
     * 恢复被删除的视频
     *
     * @param videoId 视频ID
     */
    @Operation(summary = "恢复被删除的视频")
    @PutMapping(path = "/{videoId}")
    public ResponseVO <Object> recoverVideo(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        videoService.recoverVideo(videoId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "切换视频推荐状态")
    @PutMapping(path = "/recommend/{videoId}")
    public ResponseVO <Object> toggleVideoRecommend(@RequestParam("videoId") @NotNull Long videoId)
    {
        videoService.toggleVideoRecommend(videoId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "删除用户视频")
    @DeleteMapping(path = "/video/{userId}/{videoId}")
    public ResponseVO <Object> deleteVideo(@PathVariable(name = "userId") @NotNull Long userId,
                                           @PathVariable(name = "videoId") @NotNull Long videoId,
                                           @RequestParam(name = "detail") @NotEmpty String detail)
    {
        videoService.deleteVideo(userId, videoId, detail);
        return ResponseVO.success(null);
    }

}
