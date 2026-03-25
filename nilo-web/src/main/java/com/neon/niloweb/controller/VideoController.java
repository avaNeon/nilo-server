package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.enums.videoInfo.RecommendType;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public ResponseVO <List <VideoInfo>> loadRecommendVideo()
    {
        VideoInfoQuery infoQuery = new VideoInfoQuery();
        infoQuery.setOrderBy("create_time desc");
        infoQuery.setRecommendType(RecommendType.RECOMMENDED.getType());
        return ResponseVO.success(videoService.selectList(infoQuery));
    }
}
