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

    /**
     * 通过一个相对文件路径获取图片文件
     * @param sourcePath 相对路径
     * @return 图片文件
     */
    @Operation(summary = "获取图片")
    @GetMapping("/image")
    public ResponseVO <Object> downloadImage(@Parameter(hidden = true) HttpServletResponse response,
                                             @RequestParam(name = "sourcePath") @NotNull String sourcePath)
    {
        fileService.downloadImage(response, sourcePath);
        return ResponseVO.success(null);
    }

    @Operation(summary = "预上传视频", description = "上传视频标签")
    @PostMapping("/videoTag")
    public ResponseVO <Long> preUploadVideo(@NotEmpty @RequestParam(name = "filename") String filename,
                                            @NotNull @RequestParam(name = "chunkSize") Integer chunkSize,
                                            @RequestHeader(name = "token") String token)
    {
        TokenUserInfo tokenUserInfo = getLoginState(token);
        Long uploadId = fileService.preUploadVideo(filename, chunkSize, tokenUserInfo);
        return ResponseVO.success(uploadId);
    }

    @Operation(summary = "上传视频文件")
    @PostMapping("/video")
    public ResponseVO <Object> uploadVideo(@RequestParam(name = "chunkFile") @NotNull MultipartFile chunkFile,
                                           @RequestParam(name = "chunkIndex") @NotNull Integer chunkIndex,
                                           @RequestParam(name = "uploadId") @NotEmpty String uploadId,
                                           @RequestHeader(name = "token") String token)
    {
        TokenUserInfo tokenUserInfo = getLoginState(token);
        Long userId = tokenUserInfo.getUserId();
        fileService.uploadVideo(chunkFile, chunkIndex, userId, uploadId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "删除上传的视频文件")
    @DeleteMapping("/video")
    public ResponseVO <Object> deleteVideo(@RequestParam(name = "uploadId") @NotEmpty String uploadId,
                                           @RequestHeader(name = "token") String token)
    {
        TokenUserInfo loginState = getLoginState(token);
        Long userId = loginState.getUserId();
        fileService.deleteVideo(uploadId, userId);
        return ResponseVO.success(null);
    }

    /**
     * 检查登录状态【暂时的策略】
     *
     * @param token 用户登录信息token
     * @return 在Redis保存的TokenUserInfo对象
     */
    private TokenUserInfo getLoginState(String token)
    {
        TokenUserInfo tokenUserInfo = (TokenUserInfo) redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        // 如果还没登录，就返回“未登录”的业务异常
        if (tokenUserInfo == null) throw new BusinessException(ResponseCode.NOT_LOGIN);
        else return tokenUserInfo;
    }
}
