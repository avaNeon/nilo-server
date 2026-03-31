package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.dto.VideoInfoUploadJoinDTO;
import com.neon.nilocommon.entity.dto.VideoUploadDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.VideoStatusCountVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.service.CreativeCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "创作中心视频上传管理")
@RequestMapping(path = "/creativeCenter")
@Validated
@RequiredArgsConstructor
@RestController
public class CreativeCenterController
{
    private final RedisTemplate <String, Object> redisTemplate;

    private final CreativeCenterService creativeCenterService;

    /**
     * 上传/修改视频
     *
     * @param token          验证用户身份
     * @param videoUploadDTO 视频上传信息DTO
     */
    @Operation(summary = "上传/修改视频")
    @PostMapping(path = "/video")
    public ResponseVO <Object> videoUpload(@RequestHeader(name = "token") String token,
                                           @RequestBody @Valid @NotNull VideoUploadDTO videoUploadDTO)
    {
        TokenUserInfo tokenUserInfo = getLoginState(token);
        List <Long> uploadIdList = videoUploadDTO.getUploadIdList();
        if (uploadIdList == null || uploadIdList.isEmpty())
        {
            throw new BusinessException(ResponseCode.SERVER_ERROR); // 文件为什么是空的！
        }
        List <VideoInfoFileUpload> uploadFileList = uploadIdList.stream().map(uploadId ->
                                                                              {
                                                                                  VideoInfoFileUpload fileUpload = new VideoInfoFileUpload();
                                                                                  fileUpload.setUploadId(uploadId);
                                                                                  return fileUpload;
                                                                              }).toList();
        creativeCenterService.videoUpload(videoUploadDTO.getVideoId(),
                                          videoUploadDTO.getCoverPath(),
                                          videoUploadDTO.getVideoTitle(),
                                          videoUploadDTO.getPCategoryId(),
                                          videoUploadDTO.getCategoryId(),
                                          videoUploadDTO.getPostType(),
                                          videoUploadDTO.getTags(),
                                          videoUploadDTO.getIntroduction(),
                                          videoUploadDTO.getInteraction(),
                                          uploadFileList,
                                          tokenUserInfo);
        return ResponseVO.success(null);
    }

    /**
     * 查询用户上传视频
     *
     * @param status    视频状态
     * @param pageNo    页号
     * @param pageSize  页大小
     * @param nameFuzzy 名称（模糊搜索）
     * @return 查询结果（视频列表）
     */
    @Operation(summary = "获取视频列表")
    @GetMapping(path = "/video/list")
    public ResponseVO <List <VideoInfoUploadJoinDTO>> loadVideoList(@RequestHeader(name = "token") String token,
                                                                    @RequestParam(name = "status") Short status,
                                                                    @RequestParam(name = "pageNo") Integer pageNo,
                                                                    @RequestParam(name = "pageSize") Integer pageSize,
                                                                    @RequestParam(name = "nameFuzzy") String nameFuzzy)
    {
        TokenUserInfo tokenUserInfo = getLoginState(token);
        List <VideoInfoUploadJoinDTO> result = creativeCenterService.loadVideoList(tokenUserInfo, status, pageNo, pageSize, nameFuzzy);
        return ResponseVO.success(result);
    }

    /**
     * 获取不同状态视频的数量
     *
     * @return 三种状态视频的数量
     */
    @GetMapping(path = "/video/count")
    public ResponseVO <VideoStatusCountVO> getVideoStatusCount(@RequestHeader(name = "token") String token)
    {
        TokenUserInfo tokenUserInfo = getLoginState(token);
        VideoStatusCountVO videoStatusCount = creativeCenterService.getVideoStatusCount(tokenUserInfo);
        return ResponseVO.success(videoStatusCount);
    }

    /**
     * 检查登录状态【暂时的策略】
     *
     * @param token 用户登录信息token
     * @return 在Redis保存的TokenUserInfo对象（一定会返回一个非null的值，否则会抛出<b>未登录</b>的异常）
     */
    private TokenUserInfo getLoginState(String token)
    {
        TokenUserInfo tokenUserInfo = (TokenUserInfo) redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        // 如果还没登录，就抛出“未登录”的业务异常
        if (tokenUserInfo == null) throw new BusinessException(ResponseCode.NOT_LOGIN);
        else return tokenUserInfo;
    }
}
