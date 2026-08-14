package com.neon.niloweb.controller;

import com.neon.nilocommon.annotation.RateLimit;
import com.neon.nilocommon.entity.enums.RateLimitType;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.videoInfo.BasicVideoInfo;
import com.neon.nilocommon.entity.vo.videoSeriesInfo.VideoSeriesInfoVO;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.service.VideoSeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "视频系列接口", description = "与视频系列相关的操作")
@Validated
@RequiredArgsConstructor
@RequestMapping("/series")
@RestController
public class VideoSeriesController
{
    private final VideoSeriesService videoSeriesService;

    private final LoginState loginState;

    @RateLimit
    @Operation(summary = "获取所有视频系列", description = "每个视频系列都带有第一个视频的封面，除非没有视频")
    @GetMapping("/{userId}/series")
    public ResponseVO <List <VideoSeriesInfoVO>> loadVideoSeries(@PathVariable(name = "userId") @NotNull Long userId)
    {
        return ResponseVO.success(videoSeriesService.loadVideoSeries(userId));
    }

    @RateLimit
    @Operation(summary = "根据系列ID获取视频系列信息")
    @GetMapping("/series/{seriesId}")
    public ResponseVO <VideoSeriesInfoVO> getVideoSeriesInfoBySeriesId(@PathVariable(name = "seriesId") @NotNull Long seriesId)
    {
        return ResponseVO.success(videoSeriesService.getVideoSeriesInfoBySeriesId(seriesId));
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "重新排序视频系列")
    @PutMapping(path = "/resort")
    public ResponseVO <Object> resortVideoSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                                 @RequestBody @NotNull List <Long> seriesIdList)
    {
        long userId = loginState.getLoginUserId(token);
        videoSeriesService.resortVideoSeries(userId, seriesIdList);
        return ResponseVO.success(null);
    }

    /**
     * 新增/修改视频系列 <hr/>
     * 可以允许系列名称重名
     */
    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "新增/修改视频系列", description = "若为修改系列，则按请求体视频列表覆盖系列内视频并重新排序")
    @PostMapping(path = "/series")
    public ResponseVO <Object> saveVideoSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                               @RequestParam(name = "seriesId", required = false) Long seriesId,
                                               @RequestParam(name = "seriesName") @NotEmpty @Size(min = 1, max = 100)
                                               String seriesName,
                                               @RequestParam(name = "seriesDescription") @Size(max = 300)
                                               String seriesDescription,
                                               @RequestBody @NotNull List <Long> videoIdList)
    {
        long userId = loginState.getLoginUserId(token);
        // 新增
        if (seriesId == null)
        {
            videoSeriesService.addVideoSeries(userId, seriesName, seriesDescription, videoIdList);
        }
        // 修改
        else
        {
            videoSeriesService.updateVideoSeries(userId, seriesId, seriesName, seriesDescription, videoIdList);
        }
        return ResponseVO.success(null);
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "重新排序系列中的视频", description = "请求体必须包含该系列当前全部视频ID，不会新增或删除视频")
    @PutMapping(path = "/video/resort/{seriesId}")
    public ResponseVO <Object> resortSeriesVideo(@RequestHeader(name = "token") @NotEmpty String token,
                                                 @PathVariable(name = "seriesId") @NotNull Long seriesId,
                                                 @RequestBody @NotNull List <Long> videoIdList)
    {
        long userId = loginState.getLoginUserId(token);
        videoSeriesService.resortSeriesVideo(userId, seriesId, videoIdList);
        return ResponseVO.success(null);
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "向集合中添加一条视频")
    @PostMapping("/video/{seriesId}/{videoId}")
    public ResponseVO <Object> insertVideoToSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                                   @PathVariable(name = "seriesId") @NotNull Long seriesId,
                                                   @PathVariable(name = "videoId") @NotNull Long videoId)
    {
        long userId = loginState.getLoginUserId(token);
        videoSeriesService.insertVideoToSeries(userId, seriesId, videoId);
        return ResponseVO.success(null);
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "从系列中移除一条视频")
    @DeleteMapping("/video/{seriesId}/{videoId}")
    public ResponseVO <Object> deleteSeriesVideo(@RequestHeader(name = "token") @NotEmpty String token,
                                                 @PathVariable(name = "seriesId") @NotNull Long seriesId,
                                                 @PathVariable(name = "videoId") @NotNull Long videoId)
    {
        long userId = loginState.getLoginUserId(token);
        videoSeriesService.deleteSeriesVideo(userId, seriesId, videoId);
        return ResponseVO.success(null);
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "删除视频系列")
    @DeleteMapping("/series/{seriesId}")
    public ResponseVO <Object> deleteSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                            @PathVariable(name = "seriesId") @NotNull Long seriesId)
    {
        long userId = loginState.getLoginUserId(token);
        videoSeriesService.deleteSeries(userId, seriesId);
        return ResponseVO.success(null);
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "查询有多少视频不在该集合中")
    @GetMapping("/video/ex/count/{seriesId}")
    public ResponseVO <Integer> getVideoExcludingSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                                        @PathVariable(name = "seriesId") @NotNull Long seriesId)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(videoSeriesService.getVideoExcludingSeriesCount(userId, seriesId));
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "查询不在该集合中的视频")
    @GetMapping("/video/ex/{seriesId}/{pageNo}")
    public ResponseVO <List <BasicVideoInfo>> loadMoreVideoExcludingSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                                                           @PathVariable(name = "seriesId") @NotNull
                                                                           Long seriesId,
                                                                           @PathVariable(name = "pageNo") @NotNull @Min(1)
                                                                           Integer pageNo)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(videoSeriesService.loadMoreVideoExcludingSeries(userId, seriesId, pageNo));
    }

    @RateLimit
    @Operation(summary = "查询集合中的视频数量")
    @GetMapping("/video/count/{seriesId}")
    public ResponseVO <Integer> getSeriesVideoCount(@PathVariable(name = "seriesId") @NotNull Long seriesId)
    {
        return ResponseVO.success(videoSeriesService.getSeriesVideoCount(seriesId));
    }

    @RateLimit
    @Operation(summary = "查询该集合中的视频")
    @GetMapping("/video/{seriesId}")
    public ResponseVO <List <BasicVideoInfo>> loadSeriesVideo(@PathVariable(name = "seriesId") @NotNull Long seriesId)
    {
        return ResponseVO.success(videoSeriesService.loadSeriesVideo(seriesId));
    }
}
