package com.neon.niloweb.controller;


import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.service.FileService;
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

    @Operation(summary = "获取图片")
    @GetMapping("/image")
    public ResponseVO <Object> downloadImage(@Parameter(hidden = true) HttpServletResponse response,
                                             @RequestParam(name = "sourceName") @NotNull String sourceName)
    {
        fileService.downloadImage(response, sourceName);
        return ResponseVO.success(null);
    }
}
