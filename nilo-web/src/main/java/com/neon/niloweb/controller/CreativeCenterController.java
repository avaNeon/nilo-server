package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.dto.VideoFileUploadDTO;
import com.neon.nilocommon.entity.dto.VideoInfoUploadJoinDTO;
import com.neon.nilocommon.entity.dto.VideoUploadDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.VideoInfoFileUploadVO;
import com.neon.nilocommon.entity.vo.VideoStatusCountVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import com.neon.nilocommon.entity.vo.danmaku.DanmakuManagementVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.annotation.Authorized;
import com.neon.niloweb.enums.UploadQuotaType;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.service.CategoryService;
import com.neon.niloweb.service.CreativeCenterService;
import com.neon.niloweb.service.UploadQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Tag(name = "创作中心视频管理")
@RequestMapping(path = "/creativeCenter")
@Validated
@RequiredArgsConstructor
@RestController
public class CreativeCenterController
{
    private final CreativeCenterService creativeCenterService;

    private final CategoryService categoryService;

    private final LoginState loginState;

    private final UploadQuotaService uploadQuotaService;

    /**
     * 上传/修改视频<hr/>
     * 上传的视频文件：保留文件只包含fileId和fileName，新文件只包含key和fileName
     *
     * @param token          验证用户身份
     * @param videoUploadDTO 视频上传信息DTO
     */
    @Authorized
    @Operation(summary = "上传/修改视频")
    @PostMapping(path = "/video")
    public ResponseVO <Void> videoUpload(@RequestHeader(name = "token") @NotEmpty String token,
                                         @RequestBody @Valid @NotNull VideoUploadDTO videoUploadDTO)
    {
        // 获取用户信息
        TokenUserInfo tokenUserInfo = loginState.getLoginState(token);

        // 提取上传文件列表，并转化为VideoInfoFileUpload对象
        // 这一步已经只保留了只有fileId或者key的文件，且已经去重
        List <VideoInfoFileUpload> uploadFileList = extractUploadFileList(videoUploadDTO);

        // 根据 categoryNumber 查询分类信息
        CategoryInfo matchedCategory = categoryService.findByCategoryNumber(videoUploadDTO.getCategoryNumber());


        // 调用服务层方法上传视频
        creativeCenterService.videoUpload(videoUploadDTO.getVideoId(),
                                          videoUploadDTO.getCoverPath(),
                                          videoUploadDTO.getVideoTitle(),
                                          matchedCategory.getPCategoryId(),
                                          matchedCategory.getCategoryId(),
                                          videoUploadDTO.getPostType(),
                                          videoUploadDTO.getTags(),
                                          videoUploadDTO.getIntroduction(),
                                          videoUploadDTO.getInteraction(),
                                          videoUploadDTO.getOriginInfo(),
                                          uploadFileList,
                                          uploadFileList.stream().filter(item -> item.getFileId() != null).toList(),
                                          uploadFileList.stream().filter(item -> item.getFileId() == null).toList(),
                                          tokenUserInfo);

        return ResponseVO.success();
    }

    @Operation(summary = "获取今日剩余视频上传额度", description = "返回单位：byte")
    @Authorized
    @GetMapping(path = "/video/uploadQuota")
    public ResponseVO <Long> getRemainingVideoUploadQuota(@RequestHeader(name = "token") @NotEmpty String token)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(uploadQuotaService.getRemainingQuota(userId, UploadQuotaType.VIDEO));
    }

    @Operation(summary = "获取今日剩余图片上传额度", description = "返回单位：byte")
    @Authorized
    @GetMapping(path = "/image/uploadQuota")
    public ResponseVO <Long> getRemainingImageUploadQuota(@RequestHeader(name = "token") @NotEmpty String token)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(uploadQuotaService.getRemainingQuota(userId, UploadQuotaType.IMAGE));
    }

    /**
     * 查询用户上传视频
     *
     * @param status    视频状态：<br/>
     *                  -1：进行中（包括：0：转码中、1：转码失败、2：转码成功，未审核）<br/>
     *                  3：已通过、4：未通过
     * @param pageNo    页号
     * @param pageSize  页大小
     * @param nameFuzzy 名称（模糊搜索）
     * @return 查询结果（视频列表）
     */
    @Operation(summary = "获取视频列表")
    @Authorized
    @GetMapping(path = "/video/list")
    public ResponseVO <List <VideoInfoUploadJoinDTO>> loadVideoList(@RequestHeader(name = "token") @NotEmpty String token,
                                                                    @RequestParam(name = "status", required = false) Short status,
                                                                    @RequestParam(name = "pageNo") @Min(1) Integer pageNo,
                                                                    @RequestParam(name = "pageSize") @Max(10) @Min(1)
                                                                    Integer pageSize,
                                                                    @RequestParam(name = "nameFuzzy", required = false)
                                                                    String nameFuzzy)
    {
        TokenUserInfo tokenUserInfo = loginState.getLoginState(token);
        List <VideoInfoUploadJoinDTO> result = creativeCenterService.loadVideoList(tokenUserInfo,
                                                                                   status,
                                                                                   pageNo,
                                                                                   pageSize,
                                                                                   nameFuzzy);
        return ResponseVO.success(result);
    }

    /**
     * 获取不同状态视频的数量
     *
     * @param nameFuzzy 模糊视频名称
     * @return 三种状态视频的数量
     */
    @Operation(summary = "获取视频数量")
    @Authorized
    @GetMapping(path = "/video/count")
    public ResponseVO <VideoStatusCountVO> getVideoStatusCount(@RequestHeader(name = "token") String token,
                                                               @RequestParam(name = "nameFuzzy", required = false)
                                                               String nameFuzzy)
    {
        TokenUserInfo tokenUserInfo = loginState.getLoginState(token);
        VideoStatusCountVO videoStatusCount = creativeCenterService.getVideoStatusCount(tokenUserInfo, nameFuzzy);
        return ResponseVO.success(videoStatusCount);
    }

    @Operation(summary = "获取视频上传文件信息",
               description = "获取指定视频文件的所有上传文件的简单信息，包括文件名、文件索引、持续时间、文件大小、上传ID")
    @Authorized
    @GetMapping(path = "/file/{videoId}")
    public ResponseVO <List <VideoInfoFileUploadVO>> loadVideoFileUpload(@RequestHeader(name = "token") @NotEmpty String token,
                                                                         @PathVariable(name = "videoId") @NotNull Long videoId)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(creativeCenterService.loadVideoFileUpload(videoId, userId));
    }

    @Operation(summary = "更改视频互动权限")
    @Authorized
    @PostMapping("/video/interaction/{videoId}")
    public ResponseVO <Void> setInteraction(@RequestHeader(name = "token") @NotEmpty String token,
                                              @PathVariable(name = "videoId") @NotNull Long videoId,
                                              @RequestParam(name = "interaction") String interaction)
    {
        long userId = loginState.getLoginUserId(token);
        creativeCenterService.setInteraction(userId, videoId, interaction);
        return ResponseVO.success();
    }

    @Operation(summary = "用户删除视频")
    @Authorized
    @DeleteMapping(path = "/video/{videoId}")
    public ResponseVO <Void> deleteVideo(@RequestHeader(name = "token") @NotEmpty String token,
                                         @PathVariable(name = "videoId") @NotNull Long videoId,
                                         @RequestParam(name = "detail") @NotEmpty String detail)
    {
        long userId = loginState.getLoginUserId(token);
        creativeCenterService.deleteVideo(userId, videoId, detail);
        return ResponseVO.success();
    }

    @Operation(summary = "获取评论管理信息数量")
    @Authorized
    @GetMapping(path = "/comment/count")
    public ResponseVO <Long> getCommentManagementInfoCount(@RequestHeader(name = "token") @NotEmpty String token,
                                                           @RequestParam(name = "videoId", required = false) Long videoId,
                                                           @RequestParam(name = "nameFuzzy", required = false) String nameFuzzy)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(creativeCenterService.getCommentManagementInfoCount(userId, videoId, nameFuzzy));
    }

    @Operation(summary = "获取评论管理信息")
    @Authorized
    @GetMapping(path = "/comment/{pageNo}/{pageSize}")
    public ResponseVO <List <CommentManagementVO>> getCommentManagementInfo(@RequestHeader(name = "token") @NotEmpty String token,
                                                                            @RequestParam(name = "videoId", required = false)
                                                                            Long videoId,
                                                                            @PathVariable(name = "pageNo") @NotNull @Min(1)
                                                                            Integer pageNo,
                                                                            @PathVariable(name = "pageSize") @Min(1) @Max(10)
                                                                            @NotNull Integer pageSize,
                                                                            @RequestParam(name = "nameFuzzy", required = false)
                                                                            String nameFuzzy)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(creativeCenterService.getCommentManagementInfo(userId, videoId, nameFuzzy, pageNo, pageSize));
    }

    @Operation(summary = "获取弹幕管理信息数量")
    @Authorized
    @GetMapping(path = "/danmaku")
    public ResponseVO <Long> getDanmakuManagementInfoCount(@RequestHeader(name = "token") @NotEmpty String token,
                                                           @RequestParam(name = "videoId", required = false) Long videoId,
                                                           @RequestParam(name = "fileIndex", required = false) Integer fileIndex,
                                                           @RequestParam(name = "nameFuzzy", required = false) String nameFuzzy)
    {
        long userId = loginState.getLoginUserId(token);
        // 如果视频都没有指定，就更别提分P了
        if (videoId == null)
        {
            fileIndex = null;
        }
        return ResponseVO.success(creativeCenterService.getDanmakuManagementInfoCount(userId, videoId, fileIndex, nameFuzzy));
    }

    @Operation(summary = "获取弹幕管理信息")
    @Authorized
    @GetMapping(path = "/danmaku/{pageNo}/{pageSize}")
    public ResponseVO <List <DanmakuManagementVO>> getDanmakuManagementInfo(@RequestHeader(name = "token") @NotEmpty String token,
                                                                            @RequestParam(name = "videoId", required = false)
                                                                            Long videoId,
                                                                            @RequestParam(name = "fileIndex", required = false)
                                                                            Integer fileIndex,
                                                                            @RequestParam(name = "nameFuzzy", required = false)
                                                                            String nameFuzzy,
                                                                            @PathVariable(name = "pageNo") @NotNull @Min(1)
                                                                            Integer pageNo,
                                                                            @PathVariable(name = "pageSize") @Min(1) @Max(10)
                                                                            @NotNull Integer pageSize)
    {
        long userId = loginState.getLoginUserId(token);
        // 如果视频都没有指定，就更别提分P了
        if (videoId == null)
        {
            fileIndex = null;
        }
        return ResponseVO.success(creativeCenterService.getDanmakuManagementInfo(userId,
                                                                                 videoId,
                                                                                 fileIndex,
                                                                                 nameFuzzy,
                                                                                 pageNo,
                                                                                 pageSize));
    }

    /**
     * 从DTO中提取有效视频文件列表，保留请求顺序并重新标记fileIndex
     *
     * @param videoUploadDTO 视频上传DTO
     * @return 提取合并后的视频文件上传列表
     */
    private List <VideoInfoFileUpload> extractUploadFileList(VideoUploadDTO videoUploadDTO)
    {
        List <VideoFileUploadDTO> videoFileUploadList = videoUploadDTO.getVideoFileUploadList() == null ? Collections.emptyList() : videoUploadDTO.getVideoFileUploadList();

        // 只保留fileId/key二选一的记录，旧文件按fileId去重，新文件按key去重
        // 不会改变上传时的相对顺序
        List <VideoInfoFileUpload> uploadFileList = videoFileUploadList.stream()
                                                                       .filter(Objects::nonNull)
                                                                       .filter(item -> (item.getFileId() == null) != (item.getKey() == null)) // 只能二选一
                                                                       .map(item ->
                                                                            {
                                                                                VideoInfoFileUpload uploadFile = new VideoInfoFileUpload();
                                                                                uploadFile.setFileId(item.getFileId());
                                                                                uploadFile.setFileName(item.getFilename());
                                                                                uploadFile.setFilePath(item.getKey());
                                                                                return uploadFile;
                                                                            })
                                                                       .collect(Collectors.toMap(item -> item.getFileId() != null ? "fileId:" + item.getFileId() : "key:" + item.getFilePath(),
                                                                                                 item -> item,
                                                                                                 (existing, replacement) -> existing,
                                                                                                 LinkedHashMap::new)) // 去重
                                                                       .values()
                                                                       .stream()
                                                                       .toList();

        // 上传文件列表不能为空
        if (uploadFileList.isEmpty())
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 标好顺序
        for (int i = 0 ; i < uploadFileList.size() ; i++)
        {
            uploadFileList.get(i).setFileIndex(i + 1);
        }

        return uploadFileList;
    }
}
