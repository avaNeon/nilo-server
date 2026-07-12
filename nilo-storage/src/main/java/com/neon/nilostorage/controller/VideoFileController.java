package com.neon.nilostorage.controller;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilostorage.service.VideoFileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RequestMapping(path = "/video")
@Validated
@RestController
@RequiredArgsConstructor
public class VideoFileController
{
    private final VideoFileService videoFileService;


    @Operation(summary = "请求用于上传的 presigned post form")
    @GetMapping(path = "/upload")
    public ResponseVO <Map <String, String>> upload(@RequestParam(name = "key") @NotEmpty String key,
                                                    @RequestParam(name = "expireSeconds") @NotNull @Min(1) Long expireSeconds,
                                                    @RequestParam(name = "maxBytes") @NotNull @Min(1) Long maxBytes)
    {
        return ResponseVO.success(videoFileService.upload(key, expireSeconds, maxBytes));
    }

    @Operation(summary = "移动视频文件")
    @PutMapping(path = "/move")
    public ResponseVO <Void> move(@RequestParam(name = "srcKey") @NotEmpty String srcKey,
                                  @RequestParam(name = "destKey") @NotEmpty String destKey)
    {
        videoFileService.move(srcKey, destKey);
        return ResponseVO.success();
    }

    @Operation(summary = "批量移动视频文件")
    @PutMapping(path = "/move/batch")
    public ResponseVO <Void> batchMove(@RequestBody @NotEmpty Map <String, String> keyMap)
    {
        videoFileService.batchMove(keyMap);
        return ResponseVO.success();
    }

    @Operation(summary = "批量移动视频目录")
    @PutMapping(path = "/move/directory/batch")
    public ResponseVO <Void> batchMoveDirectory(@RequestBody @NotEmpty Map <String, String> directoryMap)
    {
        videoFileService.batchMoveDirectory(directoryMap);
        return ResponseVO.success();
    }

    @Operation(summary = "递归删除视频文件")
    @DeleteMapping(path = "/delete/recursively")
    public ResponseVO <Void> deleteRecursively(@RequestBody @NotEmpty String baseKey)
    {
        videoFileService.deleteRecursively(baseKey);
        return ResponseVO.success();
    }

    /**
     * 递归删除所有状态的文件
     */
    @Operation(summary = "批量递归删除视频文件")
    @DeleteMapping(path = "/delete/recursively/batch")
    public ResponseVO <Void> batchDeleteRecursively(@RequestBody @NotEmpty List <String> baseKeys)
    {
        videoFileService.batchDeleteRecursively(baseKeys);
        return ResponseVO.success();
    }

    @Operation(summary = "删除单个视频对象")
    @DeleteMapping(path = "/delete/object")
    public ResponseVO <Void> deleteObject(@RequestBody @NotEmpty String key)
    {
        videoFileService.deleteObject(key);
        return ResponseVO.success();
    }

    @Operation(summary = "获取视频文件大小")
    @GetMapping(path = "/size")
    public ResponseVO <Long> getVideoFileSize(@RequestParam(name = "key") @NotEmpty String key)
    {
        return ResponseVO.success(videoFileService.getVideoFileSize(key));
    }

    @Operation(summary = "在所有前缀中探测视频文件大小")
    @GetMapping(path = "/size/probe")
    public ResponseVO <Long> probeVideoFileSize(@RequestParam(name = "baseKey") @NotEmpty String baseKey)
    {
        return ResponseVO.success(videoFileService.probeVideoFileSize(baseKey));
    }

    @Operation(summary = "请求用于下载 pending 状态 HLS 视频文件的 presigned url")
    @GetMapping(path = "/download")
    public ResponseVO <String> downloadVideo(@RequestParam(name = "key") @NotEmpty String key,
                                             @RequestParam(name = "expireSeconds") @NotNull @Min(1) Integer expireSeconds)
    {
        return ResponseVO.success(videoFileService.downloadVideo(key, expireSeconds));
    }

}
