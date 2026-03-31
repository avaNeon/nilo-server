package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.vo.*;
import com.neon.niloweb.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "视频管理")
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/video")
public class VideoController
{
    private final VideoService videoService;

    /**
     * 查询推荐视频
     *
     * @return 推荐视频列表
     */
    @Operation(summary = "查询推荐视频接口", description = "查询推荐视频列表，按照创建时间倒序排序")
    @GetMapping(path = "/recommend")
    public ResponseVO <List <BriefVideoInfoVO>> loadRecommendVideo()
    {
        return ResponseVO.success(videoService.loadRecommendVideo());
    }

    /**
     * 查询视频列表
     *
     * @return 分页视频列表
     */
    @Operation(summary = "查询视频接口", description = "查询视频列表，按照创建时间倒序排序")
    @GetMapping(path = "/video")
    public ResponseVO <PaginationResponseVO <BriefVideoInfoVO>> loadVideo(
            @RequestParam(name = "categoryNumber", required = false) String categoryNumber,
            @RequestParam(name = "pageNo", required = false) Integer pageNo)
    {
        return ResponseVO.success(videoService.loadVideo(categoryNumber, pageNo));
    }

    /**
     * 查询视频详细信息
     *
     * @param videoId 视频ID
     * @return 视频详细信息
     */
    @Operation(summary = "查询视频详细信息", description = "查询视频详细信息，包括用户信息、创建时间、分类信息、标签、简介等")
    @GetMapping(path = "/video/{videoId}")
    public ResponseVO <VideoInfoVO> loadVideoInfo(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoService.loadVideoInfo(videoId));
    }

    @Operation(summary = "获取所有分P文件信息", description = "获取所有分P文件的简单信息，只包括文件名、文件索引、持续时间")
    @GetMapping(path = "/file/{videoId}")
    public ResponseVO <List <VideoInfoFileVO>> loadVideoFile(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoService.loadVideoFile(videoId));
    }
}
