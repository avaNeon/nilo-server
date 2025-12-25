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
    public ResponseVO <Object> downloadImage(@Parameter(hidden = true) HttpServletResponse response,
                                             @RequestParam(name = "sourceName") @NotNull String sourceName)
    {
        fileService.downloadImage(response, sourceName);
        return ResponseVO.success(null);
    }
}
