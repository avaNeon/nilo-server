package com.neon.niloweb.controller;


import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
     * 上传图片，可以选择是否生成缩略图
     *
     * @return 图片文件相对路径
     */
    @Operation(summary = "上传图片")
    @PutMapping("/image")
    public ResponseVO <String> uploadImage(@RequestParam(name = "file") @NotNull MultipartFile file,
                                           @RequestParam(name = "createThumbnail") @NotNull Boolean createThumbnail)
    {
        return ResponseVO.success(fileService.uploadImage(file, createThumbnail));
    }

    /**
     * 通过一个相对文件路径获取图片文件
     *
     * @param sourcePath 相对路径（必须是 xxx/xx 格式）
     */
    @Operation(summary = "获取图片")
    @GetMapping("/image")
    public void downloadImage(@Parameter(hidden = true) HttpServletResponse response,
                              @RequestParam(name = "sourcePath") @Parameter(description = "必须是 xxx/ xx 格式") @NotNull
                              String sourcePath,
                              @RequestParam(name = "tmp", required = false, defaultValue = "false") Boolean tmp)
    {
        if (tmp == null)
        {
            throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
        }
        fileService.downloadImage(response, sourcePath, tmp);
    }

    /**
     * 预上传视频文件<hr/>
     * 上传视频文件名称，分块数，以及验证用户token<br/>
     * 会在redis里保存一个临时的记录
     *
     * @return uploadId，用于指定唯一视频文件（一个视频文件可能被分为多个块）
     */
    @Operation(summary = "预上传视频文件", description = "上传视频文件分块数")
    @PostMapping("/videoTag")
    public ResponseVO <Long> preUploadVideo(@NotNull @RequestParam(name = "chunkSize") Integer chunkSize,
                                            @RequestHeader(name = "token") String token)
    {
        TokenUserInfo tokenUserInfo = loginState.getLoginState(token);
        Long uploadId = fileService.preUploadVideo(chunkSize, tokenUserInfo);
        return ResponseVO.success(uploadId);
    }

    /**
     * 上传视频文件（的一块）<hr/>
     * 上传具体视频文件，通过uploadId指明所属具体视频，通过chunkIndex指明是第几块，同时还要用token验证用户身份<br/>
     * 会将视频文件临时保存在服务器中，同时更新redis中的记录
     */
    @Operation(summary = "上传单块视频文件")
    @PostMapping("/video")
    public ResponseVO <Object> uploadVideo(@RequestParam(name = "chunkFile") @NotNull MultipartFile chunkFile,
                                           @RequestParam(name = "chunkIndex") @NotNull Integer chunkIndex,
                                           @RequestParam(name = "uploadId") @NotNull Long uploadId,
                                           @RequestHeader(name = "token") String token)
    {
        long userId = loginState.getLoginUserId(token);
        fileService.uploadVideo(chunkFile, chunkIndex, userId, uploadId);
        return ResponseVO.success(null);
    }

    /**
     * 删除上传的视频文件
     *
     * @param uploadId uploadId
     * @param token    token
     * @return 无内容
     */
    @Operation(summary = "删除上传的视频文件")
    @DeleteMapping("/video")
    public ResponseVO <Object> deleteVideo(@RequestParam(name = "uploadId") @NotNull Long uploadId,
                                           @RequestHeader(name = "token") String token)
    {
        long userId = loginState.getLoginUserId(token);
        fileService.deleteVideo(uploadId, userId);
        return ResponseVO.success(null);
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
