package com.neon.niloweb.controller;


import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate <String, Object> redisTemplate;

    @Operation(summary = "获取图片")
    @GetMapping("/image")
    public ResponseVO <Object> downloadImage(@Parameter(hidden = true) HttpServletResponse response,
                                             @RequestParam(name = "sourceName") @NotNull String sourceName)
    {
        fileService.downloadImage(response, sourceName);
        return ResponseVO.success(null);
    }

    @Operation(summary = "预上传视频")
    @PostMapping("/preupload")
    public ResponseVO <Long> preUploadVideo(@NotEmpty @RequestParam(name = "filename") String filename,
                                            @NotNull @RequestParam(name = "chunkSize") Integer chunkSize,
                                            @RequestHeader(name = "token") String token)
    {
        TokenUserInfo tokenUserInfo = (TokenUserInfo) redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        // 如果还没登录，就返回“未登录”的业务异常
        if (tokenUserInfo == null) throw new BusinessException(ResponseCode.NOT_LOGIN);
        Long uploadId = fileService.preUploadVideo(filename, chunkSize, tokenUserInfo);
        return ResponseVO.success(uploadId);
    }

    @Operation(summary = "上传视频")
    @PostMapping("/video")
    public ResponseVO <Object> uploadVideo(@RequestParam(name = "chunkFile") @NotNull MultipartFile chunkFile,
                                           @RequestParam(name = "chunkIndex") @NotNull Integer chunkIndex,
                                           @RequestParam(name = "uploadId") @NotEmpty String uploadId,
                                           @RequestHeader(name = "token") String token)
    {
        TokenUserInfo tokenUserInfo = (TokenUserInfo) redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        // 如果还没登录，就返回“未登录”的业务异常（虽然很奇怪）
        if (tokenUserInfo == null) throw new BusinessException(ResponseCode.NOT_LOGIN);

        Long userId = tokenUserInfo.getUserId();
        fileService.uploadVideo(chunkFile, chunkIndex, userId, uploadId);
        return ResponseVO.success(null);
    }
}
