package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.dto.VideoInfoUploadJoinDTO;
import com.neon.nilocommon.entity.dto.VideoUploadDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.VideoFileUploadDTO;
import com.neon.nilocommon.entity.vo.VideoInfoFileUploadVO;
import com.neon.nilocommon.entity.vo.VideoStatusCountVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import com.neon.nilocommon.entity.vo.danmaku.DanmakuManagementVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.loginState.LoginState;
import com.neon.niloweb.repository.redis.CategoryRedisRepository;
import com.neon.niloweb.service.CreativeCenterService;
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

import java.util.List;

@Tag(name = "创作中心视频管理")
@RequestMapping(path = "/creativeCenter")
@Validated
@RequiredArgsConstructor
@RestController
public class CreativeCenterController
{
    private final CreativeCenterService creativeCenterService;

    private final CategoryRedisRepository categoryRedisRepository;

    private final LoginState loginState;

    /**
     * 上传/修改视频<hr/>
     * 上传的视频文件只包含uploadId和fileName
     *
     * @param token          验证用户身份
     * @param videoUploadDTO 视频上传信息DTO
     */
    @Operation(summary = "上传/修改视频")
    @PostMapping(path = "/video")
    public ResponseVO <Object> videoUpload(@RequestHeader(name = "token") @NotEmpty String token,
                                           @RequestBody @Valid @NotNull VideoUploadDTO videoUploadDTO)
    {
        TokenUserInfo tokenUserInfo = loginState.getLoginState(token);
        List <VideoFileUploadDTO> uploadIdList = videoUploadDTO.getVideoFileUploadList();
        if (uploadIdList == null || uploadIdList.isEmpty())
        {
            throw new BusinessException(ResponseCode.SERVER_ERROR); // 文件为什么是空的！
        }

        String categoryNumber = videoUploadDTO.getCategoryNumber();
        List <CategoryInfo> categories = categoryRedisRepository.getCategoryInfo();
        if (categories == null || categories.isEmpty())
        {
            throw new BusinessException(ResponseCode.SERVER_ERROR); // 分类数据不可用
        }
        CategoryInfo matchedCategory = categories.stream()
                                                 .flatMap(c ->
                                                          {
                                                              List <CategoryInfo> all = new java.util.ArrayList <>();
                                                              all.add(c);
                                                              if (c.getChildren() != null) all.addAll(c.getChildren());
                                                              return all.stream();
                                                          })
                                                 .filter(c -> categoryNumber.equals(c.getCategoryNumber()))
                                                 .findFirst()
                                                 .orElseThrow(() -> new BusinessException(ResponseCode.SERVER_ERROR)); // 无效的分类编码

        List <VideoInfoFileUpload> uploadFileList = uploadIdList.stream().map(vo ->
                                                                              {
                                                                                  VideoInfoFileUpload fileUpload = new VideoInfoFileUpload();
                                                                                  fileUpload.setUploadId(vo.getUploadId());
                                                                                  fileUpload.setFileName(vo.getFilename());
                                                                                  return fileUpload;
                                                                              }).toList();
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
                                          tokenUserInfo);
        return ResponseVO.success(null);
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
    @GetMapping(path = "/file/{videoId}")
    public ResponseVO <List <VideoInfoFileUploadVO>> loadVideoFileUpload(@RequestHeader(name = "token") @NotEmpty String token,
                                                                         @PathVariable(name = "videoId") @NotNull Long videoId)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(creativeCenterService.loadVideoFileUpload(videoId, userId));
    }

    @Operation(summary = "更改视频互动权限")
    @PostMapping("/video/interaction/{videoId}")
    public ResponseVO <Object> setInteraction(@RequestHeader(name = "token") @NotEmpty String token,
                                              @PathVariable(name = "videoId") @NotNull Long videoId,
                                              @RequestParam(name = "interaction") String interaction)
    {
        long userId = loginState.getLoginUserId(token);
        creativeCenterService.setInteraction(userId, videoId, interaction);
        return ResponseVO.success(null);
    }

    @Operation(summary = "用户删除视频")
    @DeleteMapping(path = "/video/{videoId}")
    public ResponseVO <Object> deleteVideo(@RequestHeader(name = "token") @NotEmpty String token,
                                           @PathVariable(name = "videoId") @NotNull Long videoId,
                                           @RequestParam(name = "detail") @NotEmpty String detail)
    {
        long userId = loginState.getLoginUserId(token);
        creativeCenterService.deleteVideo(userId, videoId, detail);
        return ResponseVO.success(null);
    }

    @Operation(summary = "获取评论管理信息数量")
    @GetMapping(path = "/comment/count")
    public ResponseVO <Long> getCommentManagementInfoCount(@RequestHeader(name = "token") @NotEmpty String token,
                                                           @RequestParam(name = "videoId", required = false) Long videoId,
                                                           @RequestParam(name = "nameFuzzy", required = false) String nameFuzzy)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(creativeCenterService.getCommentManagementInfoCount(userId, videoId, nameFuzzy));
    }

    @Operation(summary = "获取评论管理信息")
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

}
