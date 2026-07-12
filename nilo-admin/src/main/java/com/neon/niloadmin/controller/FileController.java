package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.FileService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
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
    public ResponseVO <String> uploadImage(@RequestParam(name = "file") @NotNull MultipartFile file)
    {
        return ResponseVO.success(fileService.uploadImage(file));
    }

    @Operation(summary = "获取图片预签名URL", description = "在 tmp/pending/public 前缀中自动探测图片位置")
    @GetMapping("/image")
    public ResponseVO <String> downloadImage(@RequestParam(name = "key") @NotNull String key)
    {
        return ResponseVO.success(fileService.downloadImage(key));
    }

    @Operation(summary = "下载HLS主播放列表（master.m3u8）")
    @GetMapping(path = "/video/hls/{videoId}/{index}/master.m3u8")
    public void downloadVideoMasterM3u8(@Parameter(hidden = true) HttpServletResponse response,
                                        @PathVariable(name = "videoId") @NotNull Long videoId,
                                        @PathVariable(name = "index") @NotNull Integer index)
    {
        fileService.downloadVideoMasterM3u8(response, videoId, index);
    }

    @Operation(summary = "下载HLS分辨率播放列表（index.m3u8）", description = "folder 为 720P 或 480P")
    @GetMapping(path = "/video/hls/{videoId}/{index}/{folder}/index.m3u8")
    public void downloadVideoPlaylistM3u8(@Parameter(hidden = true) HttpServletResponse response,
                                          @PathVariable(name = "videoId") @NotNull Long videoId,
                                          @PathVariable(name = "index") @NotNull Integer index,
                                          @PathVariable(name = "folder") @NotEmpty String folder)
    {
        fileService.downloadVideoPlaylistM3u8(response, videoId, index, folder);
    }

}
