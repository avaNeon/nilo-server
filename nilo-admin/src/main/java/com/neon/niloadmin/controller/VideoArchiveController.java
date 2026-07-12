package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.VideoArchiveService;
import com.neon.nilocommon.entity.dto.VideoInfoArchiveAdminJoinDTO;
import com.neon.nilocommon.entity.dto.VideoInfoArchiveAdminQueryDTO;
import com.neon.nilocommon.entity.po.VideoInfoFileArchive;
import com.neon.nilocommon.entity.query.VideoInfoArchiveQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "视频存档管理")
@RequiredArgsConstructor
@Validated
@RequestMapping(path = "/archive")
@RestController
public class VideoArchiveController
{
    private final VideoArchiveService videoArchiveService;

    @Operation(summary = "获取视频存档列表")
    @PostMapping(path = "/list")
    public ResponseVO <List <VideoInfoArchiveAdminJoinDTO>> loadVideoList(
            @RequestBody @Validated VideoInfoArchiveAdminQueryDTO queryDTO,
            @RequestParam(name = "orderByDeleteTimeAsc", required = false) Boolean orderByDeleteTimeAsc)
    {
        VideoInfoArchiveQuery archiveQuery = new VideoInfoArchiveQuery();
        BeanUtils.copyProperties(queryDTO, archiveQuery);
        if (archiveQuery.getPageSize() == null)
        {
            archiveQuery.setPageSize(10);
        }
        List <VideoInfoArchiveAdminJoinDTO> result = videoArchiveService.loadVideoList(archiveQuery, orderByDeleteTimeAsc);
        return ResponseVO.success(result);
    }

    @Operation(summary = "获取视频存档数量")
    @PostMapping(path = "/count")
    public ResponseVO <Integer> getVideoArchiveCount(@RequestBody VideoInfoArchiveAdminQueryDTO queryDTO)
    {
        VideoInfoArchiveQuery archiveQuery = new VideoInfoArchiveQuery();
        BeanUtils.copyProperties(queryDTO, archiveQuery);
        return ResponseVO.success(videoArchiveService.getVideoArchiveCount(archiveQuery));
    }

    @Operation(summary = "获取视频分P列表")
    @GetMapping(path = "/file/{videoId}")
    public ResponseVO <List <VideoInfoFileArchive>> loadVideoFileList(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoArchiveService.loadVideoFileList(videoId));
    }

    @Operation(summary = "批量删除视频存档")
    @DeleteMapping(path = "/videos")
    public ResponseVO <Object> deleteVideoArchive(@RequestParam(name = "videoIdList") @NotNull List <Long> videoIdList)
    {
        videoArchiveService.deleteVideoArchive(videoIdList);
        return ResponseVO.success(null);
    }

    @Operation(summary = "下载存档HLS主播放列表（master.m3u8）")
    @GetMapping(path = "/video/hls/{videoId}/{index}/master.m3u8")
    public void downloadVideoMasterM3u8(@PathVariable(name = "videoId") @NotNull Long videoId,
                                        @PathVariable(name = "index") @NotNull Integer index,
                                        @Parameter(hidden = true) HttpServletResponse response)
    {
        videoArchiveService.downloadVideoMasterM3u8(videoId, index, response);
    }

    @Operation(summary = "下载存档HLS分辨率播放列表（index.m3u8）", description = "folder 为 720P 或 480P")
    @GetMapping(path = "/video/hls/{videoId}/{index}/{folder}/index.m3u8")
    public void downloadVideoPlaylistM3u8(@PathVariable(name = "videoId") @NotNull Long videoId,
                                          @PathVariable(name = "index") @NotNull Integer index,
                                          @PathVariable(name = "folder") @NotEmpty String folder,
                                          @Parameter(hidden = true) HttpServletResponse response)
    {
        videoArchiveService.downloadVideoPlaylistM3u8(videoId, index, folder, response);
    }
}
