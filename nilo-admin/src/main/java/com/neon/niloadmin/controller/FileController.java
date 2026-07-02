package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.FileService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件管理")
@RequiredArgsConstructor
@Slf4j
@Validated
@RequestMapping(path = "/file")
@RestController
public class FileController
{
    private final FileService fileService;

    /**
     * 上传图片，可以选择是否生成缩略图
     */
    @Operation(summary = "上传图片")
    @PutMapping("/image")
    public ResponseVO <String> uploadImage(@RequestParam(name = "MultipartFile") @NotNull MultipartFile file,
                                           @RequestParam(name = "createThumbnail") @NotNull Boolean createThumbnail)
    {
        return ResponseVO.success(fileService.uploadImage(file, createThumbnail));
    }

    @Operation(summary = "获取图片")
    @GetMapping("/image")
    public void downloadImage(@Parameter(hidden = true) HttpServletResponse response,
                              @RequestParam(name = "sourceName") @NotNull String sourceName,
                              @RequestParam(name = "tmp", required = false, defaultValue = "false") Boolean tmp)
    {
        fileService.downloadImage(response, sourceName, tmp);
    }

    @Operation(summary = "下载HLS主播放列表（master.m3u8）")
    @GetMapping(path = "/video/hls/{videoId}/{index}/master.m3u8")
    public void downloadVideoMasterM3u8(@PathVariable(name = "videoId") @NotNull Long videoId,
                                        @PathVariable(name = "index") @NotNull Integer index,
                                        @Parameter(hidden = true) HttpServletResponse response)
    {
        fileService.downloadVideoMasterM3u8(videoId, index, response);
    }

    @Operation(summary = "下载HLS分辨率播放列表（playlist.m3u8）")
    @GetMapping(path = "/video/hls/{videoId}/{index}/playlist/{resolution}.m3u8")
    public void downloadVideoPlaylistM3u8(@PathVariable(name = "videoId") @NotNull Long videoId,
                                          @PathVariable(name = "index") @NotNull Integer index,
                                          @PathVariable(name = "resolution") @NotNull Integer resolution,
                                          @Parameter(hidden = true) HttpServletResponse response)
    {
        fileService.downloadVideoPlaylistM3u8(videoId, index, resolution, response);
    }

    @Operation(summary = "下载HLS分片（segment.ts）")
    @GetMapping(path = "/video/hls/{videoId}/{index}/segment/{resolution}/{segment}")
    public void downloadVideoSegmentTs(@PathVariable(name = "videoId") @NotNull Long videoId,
                                       @PathVariable(name = "index") @NotNull Integer index,
                                       @PathVariable(name = "resolution") @NotNull Integer resolution,
                                       @PathVariable(name = "segment") @NotNull String segment,
                                       @Parameter(hidden = true) HttpServletResponse response)
    {
        fileService.downloadVideoSegmentTs(videoId, index, resolution, segment, response);
    }
}
