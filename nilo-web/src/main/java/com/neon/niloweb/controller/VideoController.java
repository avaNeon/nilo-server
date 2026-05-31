package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.VideoInfoFileVO;
import com.neon.nilocommon.entity.vo.videoInfo.BriefVideoInfoVO;
import com.neon.nilocommon.entity.vo.videoInfo.VideoInfoVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
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

    private final RedisTemplate <String, Object> redisTemplate;

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
            @RequestParam(name = "pageNo", required = false) Integer pageNo,
            @RequestParam(name = "isRecommend", required = false) Boolean isRecommend)
    {
        return ResponseVO.success(videoService.loadVideo(categoryNumber, pageNo, isRecommend));
    }

    /**
     * 查询视频详细信息
     *
     * @param videoId 视频ID
     * @return 视频详细信息
     */
    @Operation(summary = "查询视频详细信息", description = "查询视频详细信息，包括用户信息、创建时间、分类信息、标签、简介等")
    @GetMapping(path = "/video/{videoId}")
    public ResponseVO <VideoInfoVO> loadVideoInfo(@RequestHeader(name = "token", required = false) String token,
                                                  @PathVariable(name = "videoId") @NotNull Long videoId)
    {
        Long userId = null;
        if (token != null && !token.isEmpty())
        {
            TokenUserInfo tokenUserInfo = (TokenUserInfo) redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
            if (tokenUserInfo == null)
            {
                throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
            }
            userId = tokenUserInfo.getUserInfo().getUserId();
        }
        return ResponseVO.success(videoService.loadVideoInfo(userId, videoId));
    }

    @Operation(summary = "获取所有分P文件信息", description = "获取所有分P文件的简单信息，只包括文件名、文件索引、持续时间")
    @GetMapping(path = "/file/{videoId}")
    public ResponseVO <List <VideoInfoFileVO>> loadVideoFile(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoService.loadVideoFile(videoId));
    }

    @Operation(summary = "加载热门视频列表")
    @GetMapping(path = "/hot/{pageNo}")
    public ResponseVO <List <BriefVideoInfoVO>> loadHotVideoInfo(@PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo)
    {
        return ResponseVO.success(videoService.loadHotVideos(pageNo));
    }


    @Operation(summary = "播放统计")
    @PostMapping(path = "/{videoId}")
    public ResponseVO <Object> playCount(@PathVariable("videoId") @NotNull Long videoId)
    {
        videoService.playCount(videoId);
        return ResponseVO.success(null);
    }
}
