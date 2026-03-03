package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.service.CreativeCenterVideoUploadService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "创作中心视频上传管理")
@RequestMapping(path = "/creativeCenter/video/upload")
@Validated
@RequiredArgsConstructor
@RestController
public class CreativeCenterVideoUploadController
{
    private final RedisTemplate <String, Object> redisTemplate;

    private final CreativeCenterVideoUploadService creativeCenterVideoUploadService;

//    /**
//     * 删除文件
//     */
//    @PostMapping(path = "/test/delete")
//    public ResponseVO <Object> testDelete(@RequestBody @NotNull List <String> pathList)
//    {
//        creativeCenterVideoUploadService.addVideoFile2DeleteQueue(pathList);
//        return ResponseVO.success(null);
//    }

//    /**
//     * 转码文件测试
//     */
//    @PostMapping(path = "/test/transcoding")
//    public ResponseVO <Object> testTranscoding(@RequestParam(name = "a") @NotNull Long a, @RequestParam(name = "b") @NotNull Long b)
//    {
//        List <VideoInfoFileUpload> fileUploadList = new ArrayList <>();
//        VideoInfoFileUpload file1 = new VideoInfoFileUpload();
//        file1.setVideoId(a);
//        VideoInfoFileUpload file2 = new VideoInfoFileUpload();
//        file1.setVideoId(b);
//        fileUploadList.add(file1);
//        fileUploadList.add(file2);
//        creativeCenterVideoUploadService.addVideoFile2TranscodingQueue(fileUploadList);
//        return ResponseVO.success(null);
//    }

    /**
     * 上传/修改视频
     *
     * @param token          验证用户身份
     * @param videoId        视频的唯一ID，用于区分视频
     * @param coverPath      封面在服务器的相对地址
     * @param videoTitle     视频标题
     * @param pCategoryId    所属父分类ID
     * @param categoryId     所属分类ID
     * @param postType       自制/转载
     * @param tags           标签
     * @param introduction   视频简介
     * @param interaction    互动设置
     * @param uploadFileList 视频文件列表（这里面有uploadId，因为之前文件预上传时返回了对应的id）
     */
    @PostMapping(path = "/video")
    public ResponseVO <Object> videoUpload(@RequestHeader(name = "token") String token,
                                           @RequestParam(name = "videoId") @NotNull Long videoId,
                                           @RequestParam(name = "coverPath") @NotEmpty String coverPath,
                                           @RequestParam(name = "videoTitle") @NotEmpty @Size(max = 100) String videoTitle,
                                           @RequestParam(name = "pCategoryId") @NotNull Integer pCategoryId,
                                           @RequestParam(name = "categoryId") Integer categoryId,
                                           @RequestParam(name = "postType") @NotNull Short postType,
                                           @RequestParam(name = "tags") @Size(max = 300) String tags,
                                           @RequestParam(name = "introduction") @Size(max = 2000) String introduction,
                                           @RequestParam(name = "interaction") @Size(max = 5) String interaction,
                                           @RequestBody @NotNull List <VideoInfoFileUpload> uploadFileList)
    {
        TokenUserInfo tokenUserInfo = getLoginState(token);
        if (uploadFileList.isEmpty()) return ResponseVO.error("没有视频文件"); // 为什么视频文件是空的！！！
        creativeCenterVideoUploadService.videoUpload(videoId,
                                                     coverPath,
                                                     videoTitle,
                                                     pCategoryId,
                                                     categoryId,
                                                     postType,
                                                     tags,
                                                     introduction,
                                                     interaction,
                                                     uploadFileList,
                                                     tokenUserInfo);
        return ResponseVO.success(null);
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
