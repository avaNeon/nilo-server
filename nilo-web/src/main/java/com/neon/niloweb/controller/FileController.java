package com.neon.niloweb.controller;


import com.neon.nilocommon.annotation.RateLimit;
import com.neon.nilocommon.entity.enums.RateLimitType;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.ServletUtil;
import com.neon.niloweb.annotation.Authorized;
import com.neon.niloweb.annotation.UploadQuota;
import com.neon.niloweb.enums.UploadQuotaType;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "文件管理")
@RequiredArgsConstructor
@Validated
@RequestMapping(path = "/file")
@RestController
public class FileController
{
    private final FileService fileService;

    private final LoginState loginState;

    /**
     * 上传图片<hr/>
     * 自动生成缩略图
     *
     * @return 图片文件在MinIO的key
     */
    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "上传图片", description = "上传图片，自动生成缩略图")
    @Authorized
    @PutMapping("/image")
    @UploadQuota(type = UploadQuotaType.IMAGE)
    public ResponseVO <String> uploadImage(@RequestHeader(name = "token") @NotEmpty String token,
                                           @RequestParam(name = "file") @NotNull MultipartFile file)
    {
        long userId = loginState.getLoginUserId(token);

        return ResponseVO.success(fileService.uploadImage(userId, file));
    }

    /**
     * 获取图片预签名URL<hr/>
     * 仅用于pending状态的图片，属主访问自己的待审核资源
     *
     * @param imgKey 图片key
     * @return 预签名URL
     */
    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "获取图片预签名URL")
    @Authorized
    @GetMapping("/image")
    public ResponseVO <String> downloadImage(@RequestHeader(name = "token") @NotEmpty String token,
                                             @RequestParam(name = "imgKey") @NotEmpty String imgKey)
    {
        long userId = loginState.getLoginUserId(token);

        return ResponseVO.success(fileService.downloadImage(userId, imgKey));
    }

    /**
     * 上传视频文件<hr/>
     * <p>获取上传视频文件的 presigned post form</p>
     * <ul>
     *     <li>幂等性：每次调用都会返回一个可用key</li>
     *     <li>延迟计算：每次调用计算最后一次上传文件的配额</li>
     *     <li>严格控制key：如果有没用完的key，会复用并返回，最多有一个还没使用的key，天生防止多个key并发操作越过配额</li>
     *     <li>配额控制：每次调用都会限制配额，严格限制上传大小超出配额</li>
     * </ul>
     *
     * @return presigned post form
     */
    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "获取 presigned post form", description = "获取上传视频文件的 presigned post form")
    @Authorized
    @PostMapping("/video")
    public ResponseVO <Map <String, String>> uploadVideo(@RequestHeader(name = "token") @NotEmpty String token,
                                                         @Parameter(description = "视频文件大小，单位：字节")
                                                         @RequestParam(name = "fileSize") @NotNull Long fileSize)
    {
        long userId = loginState.getLoginUserId(token);

        return ResponseVO.success(fileService.uploadVideo(userId, fileSize));
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "下载未公开视频的HLS主播放列表（master.m3u8）", description = "仅限视频发布者自己观看pending类型的视频")
    @GetMapping(path = "/video/hls/{videoId}/{index}/master.m3u8")
    public void downloadVideoMasterM3u8(@Parameter(hidden = true) HttpServletRequest request,
                                        @Parameter(hidden = true) HttpServletResponse response,
                                        @RequestHeader(name = "token") String token,
                                        @PathVariable(name = "videoId") @NotNull Long videoId,
                                        @PathVariable(name = "index") @NotNull Integer index)
    {
        long userId = loginState.getLoginUserId(resolveWebToken(token, request));
        fileService.downloadVideoMasterM3u8(userId, videoId, index, response);
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "下载未公开视频的HLS分辨率播放列表（index.m3u8）",
               description = "仅限视频发布者自己观看pending类型的视频；folder 为 720P 或 480P")
    @GetMapping(path = "/video/hls/{videoId}/{index}/{folder}/index.m3u8")
    public void downloadVideoPlaylistM3u8(@Parameter(hidden = true) HttpServletRequest request,
                                          @Parameter(hidden = true) HttpServletResponse response,
                                          @RequestHeader(name = "token") String token,
                                          @PathVariable(name = "videoId") @NotNull Long videoId,
                                          @PathVariable(name = "index") @NotNull Integer index,
                                          @PathVariable(name = "folder") @NotEmpty String folder)
    {
        long userId = loginState.getLoginUserId(resolveWebToken(token, request));
        fileService.downloadVideoPlaylistM3u8(userId, videoId, index, folder, response);
    }

    /**
     * 解析Cookie中的token<hr/>
     * HLS 请求由播放器发起，通常无法携带请求头 token，需回退到 cookie
     */
    private String resolveWebToken(String headerToken, HttpServletRequest request)
    {
        if (StringUtils.hasText(headerToken))
        {
            return headerToken;
        }

        String cookieToken = ServletUtil.getFromCookie(request, Constants.WEB_COOKIE_TOKEN_KEY);
        if (StringUtils.hasText(cookieToken))
        {
            return cookieToken;
        }

        throw new BusinessException(ResponseCode.NOT_LOGIN);
    }

}
