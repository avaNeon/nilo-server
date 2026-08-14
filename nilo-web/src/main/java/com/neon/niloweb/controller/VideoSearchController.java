package com.neon.niloweb.controller;

import com.neon.nilocommon.annotation.RateLimit;
import com.neon.nilocommon.entity.enums.videoSearch.OrderType;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoInfoDocVO;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoSearchResultVO;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.service.VideoSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "视频搜索接口")
@Validated
@RequiredArgsConstructor
@RequestMapping("/video-search")
@RateLimit
@RestController
public class VideoSearchController
{
    private final VideoSearchService videoSearchService;

    private final WebConfig webConfig;

    @Operation(summary = "搜索视频", description = "使用关键字搜索")
    @GetMapping(path = "/{orderType}/{pageNo}/{pageSize}")
    public ResponseVO <VideoSearchResultVO> searchVideo(
            @PathVariable(name = "orderType") @NotNull @Min(1) @Max(5) Short orderType,
            @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo,
            @PathVariable(name = "pageSize") @NotNull @Min(1) @Max(20) Integer pageSize,
            @RequestParam(name = "keyword") @NotEmpty String keyword)
    {
        return ResponseVO.success(videoSearchService.searchVideo(orderType, pageNo, pageSize, true, keyword, true));
    }

    @Operation(summary = "视频详情页推荐视频", description = "通过视频标题搜索相关视频，不保证传回固定行数")
    @GetMapping(path = "/{videoId}")
    public ResponseVO <List <VideoInfoDocVO>> searchRecommendVideo(@PathVariable(name = "videoId") @NotNull Long videoId,
                                                                   @RequestParam(name = "videoName") @NotEmpty String videoName)
    {
        // 查询相关视频（无需计入热搜数）
        VideoSearchResultVO videoSearchResultVO = videoSearchService.searchVideo(OrderType.MOST_PLAYED.getValue(),
                                                                                 1,
                                                                                 webConfig.getRecommendVideoSize(),
                                                                                 false,
                                                                                 videoName,
                                                                                 false);
        // 如果其中有本视频，则把本视频去掉，这就是为什么我们不能保证传回固定数量的记录
        List <VideoInfoDocVO> resultList = videoSearchResultVO.getVideoInfoDocList()
                                                              .stream()
                                                              .filter(videoInfoDocVO -> !videoInfoDocVO.getVideoId()
                                                                                                       .equals(videoId))
                                                              .toList();
        return ResponseVO.success(resultList);
    }

    @Operation(summary = "获取热搜榜单")
    @GetMapping(path = "/hot/keyword")
    public ResponseVO <List <String>> getHotKeyword()
    {
        return ResponseVO.success(videoSearchService.getHotKeyword());
    }
}
