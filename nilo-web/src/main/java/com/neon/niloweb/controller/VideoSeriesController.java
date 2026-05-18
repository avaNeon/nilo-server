package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.videoInfo.BasicVideoInfo;
import com.neon.nilocommon.entity.vo.videoSeriesInfo.VideoSeriesInfoVO;
import com.neon.nilocommon.entity.vo.videoSeriesInfo.VideoSeriesWithVideosVO;
import com.neon.nilocommon.loginState.LoginState;
import com.neon.niloweb.service.VideoSeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "视频合集接口", description = "与视频合集相关的操作")
@Validated
@RequiredArgsConstructor
@RequestMapping("/series")
@RestController
public class VideoSeriesController
{
    private final VideoSeriesService videoSeriesService;

    private final LoginState loginState;

    @Operation(summary = "分页获取视频合集", description = "每个视频合集都带有第一个视频的封面，除非没有视频")
    @GetMapping("/{userId}/series/{pageNo}/{pageSize}")
    public ResponseVO <List <VideoSeriesInfoVO>> loadVideoSeries(@PathVariable(name = "userId") @NotNull Long userId,
                                                                 @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo,
                                                                 @PathVariable(name = "pageSize") @NotNull @Min(1) @Max(10) Integer pageSize)
    {
        return ResponseVO.success(videoSeriesService.loadVideoSeries(userId, pageNo, pageSize));
    }

    @Operation(summary = "重新排序视频合集")
    @PutMapping(path = "/resort")
    public ResponseVO <Object> resortVideoSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                                 @RequestBody @NotNull List <Long> seriesIdList)
    {
        long userId = loginState.getLoginUserId(token);
        videoSeriesService.resortVideoSeries(userId, seriesIdList);
        return ResponseVO.success(null);
    }

    /**
     * 新增/修改视频合集 <hr/>
     * 可以允许合集名称重名
     */
    @Operation(summary = "新增/修改视频合集", description = "若为修改合集，则这个接口只能将合集视频重新排序")
    @PostMapping(path = "/series")
    public ResponseVO <Object> saveVideoSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                               @RequestParam(name = "seriesId", required = false) Long seriesId,
                                               @RequestParam(name = "seriesName") @NotEmpty @Size(min = 1, max = 100)
                                               String seriesName,
                                               @RequestParam(name = "seriesDescription") @NotEmpty @Size(min = 1, max = 300)
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

    @Operation(summary = "查询有多少视频不在该集合中")
    @GetMapping("/video/{seriesId}")
    public ResponseVO <Integer> getVideoExcludingSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                                        @PathVariable(name = "seriesId") @NotNull Long seriesId)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(videoSeriesService.getVideoExcludingSeriesCount(userId, seriesId));
    }

    @Operation(summary = "查询不在该集合中的视频")
    @GetMapping("/video/ex/{seriesId}/{pageNo}")
    public ResponseVO <List <BasicVideoInfo>> loadMoreVideoExcludingSeries(@RequestHeader(name = "token") @NotEmpty String token,
                                                                           @PathVariable(name = "seriesId") @NotNull Long seriesId,
                                                                           @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(videoSeriesService.loadMoreVideoExcludingSeries(userId, seriesId, pageNo));
    }

    @Operation(summary = "查询该集合中的视频")
    @GetMapping("/video/{seriesId}/{pageNo}")
    public ResponseVO <List <BasicVideoInfo>> loadSeriesVideo(@PathVariable(name = "seriesId") @NotNull Long seriesId,
                                                              @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo)
    {
        return ResponseVO.success(videoSeriesService.loadSeriesVideo(seriesId, pageNo));
    }
}
