package com.neon.nilostorage.controller.inner;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilostorage.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RequestMapping(path = "/inner/image")
@Validated
@RestController
@RequiredArgsConstructor
public class InnerImageController
{
    private final ImageService imageService;

    @Operation(summary = "上传图片", description = "自动创建缩略图")
    @PostMapping(path = "/upload")
    public ResponseVO <Void> upload(@RequestParam(name = "contentType") @NotEmpty String contentType,
                                    @RequestParam(name = "key") @NotEmpty String key,
                                    @RequestParam("file") MultipartFile file)
    {
        imageService.upload(file, contentType, key);

        return ResponseVO.success();
    }

    @Operation(summary = "移动图片")
    @PutMapping(path = "/move")
    public ResponseVO <Void> move(@RequestParam(name = "srcKey") @NotEmpty String srcKey,
                                  @RequestParam(name = "destKey") @NotEmpty String destKey)
    {
        imageService.move(srcKey, destKey);
        return ResponseVO.success();
    }

    @Operation(summary = "批量移动图片")
    @PutMapping(path = "/move/batch")
    public ResponseVO <Void> batchMove(@RequestBody @NotEmpty Map <String, String> keyMap)
    {
        imageService.batchMove(keyMap);
        return ResponseVO.success();
    }

    @Operation(summary = "删除图片")
    @DeleteMapping(path = "/delete")
    public ResponseVO <Void> delete(@RequestParam(name = "key") @NotEmpty String key)
    {
        imageService.delete(key);
        return ResponseVO.success();
    }

    /**
     * 删除所有状态的文件
     */
    @Operation(summary = "批量删除图片")
    @DeleteMapping(path = "/delete/batch")
    public ResponseVO <Void> batchDelete(@RequestBody @NotEmpty List <String> baseKeys)
    {
        imageService.batchDelete(baseKeys);
        return ResponseVO.success();
    }

    @Operation(summary = "获取图片文件大小")
    @GetMapping(path = "/size")
    public ResponseVO <Long> getImageSize(@RequestParam(name = "key") @NotEmpty String key)
    {
        return ResponseVO.success(imageService.getImageSize(key));
    }

    @Operation(summary = "探测图片在三个前缀下的完整 object key")
    @GetMapping(path = "/key/probe")
    public ResponseVO <String> probeImageObjectKey(@RequestParam(name = "baseKey") @NotEmpty String baseKey)
    {
        return ResponseVO.success(imageService.probeImageObjectKey(baseKey));
    }

    @Operation(summary = "请求用于下载的 presigned url")
    @GetMapping(path = "/download")
    public ResponseVO <String> downloadImage(@RequestParam(name = "key") @NotEmpty String key,
                                             @RequestParam(name = "expireSeconds") @NotNull @Min(1) Integer expireSeconds)
    {
        return ResponseVO.success(imageService.downloadImage(key, expireSeconds));
    }
}
