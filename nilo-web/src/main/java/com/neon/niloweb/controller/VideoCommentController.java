package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoComment.CommentOrderType;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.VideoCommentVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.EnumFieldChecker;
import com.neon.nilocommon.util.FileUtil;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.service.VideoCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.List;

@Tag(name = "视频评论接口", description = "视频评论相关接口")
@RequiredArgsConstructor
@RequestMapping("/comment")
@RestController
public class VideoCommentController
{
    private final WebConfig webConfig;

    private final RedisTemplate <String, Object> redisTemplate;

    private final VideoCommentService videoCommentService;

    @Operation(summary = "发布视频评论", description = "发布视频评论接口，登录状态通过token传递")
    @PostMapping("/comment")
    public ResponseVO <Object> postComment(@RequestHeader(name = "token") String token,
                                           @RequestParam(name = "videoId") @NotNull Long videoId,
                                           @RequestParam(name = "content", required = false) @Size(max = 1000) String content,
                                           @RequestParam(name = "imgPath", required = false) @Size(max = 150) String imgPath,
                                           @RequestParam(name = "repliedCommentId", required = false) Long repliedCommentId)
    {
        TokenUserInfo loginState = getLoginState(token);
        // 如果既没有内容也没图片就是空白评论，不合法
        if ((content == null || content.isBlank()) && (imgPath == null || imgPath.isBlank() || !FileUtil.fileExists(Paths.get(webConfig.getRootFilePath(),
                                                                                                                              Constants.FILE_FOLDER_NAME,
                                                                                                                              Constants.COVER_FOLDER_NAME)
                                                                                                                         .toString(),
                                                                                                                    imgPath)))
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        videoCommentService.postComment(loginState.getUserInfo().getUserId(),
                                        videoId,
                                        content,
                                        imgPath,
                                        repliedCommentId == null ? 0 : repliedCommentId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "获取视频评论列表",
               description = "获取视频评论列表接口，登录状态通过token传递，页大小为10，子评论页大小为5，深度默认为3")
    @GetMapping(path = "/comment")
    public ResponseVO <List <VideoCommentVO>> getCommentList(@RequestParam(name = "videoId") @NotNull Long videoId,
                                                             @RequestParam(name = "parentCommentId") @NotNull Long parentCommentId,
                                                             @RequestParam(name = "pageNo") @NotNull @Positive Integer pageNo,
                                                             @RequestParam(name = "orderType") @NotEmpty
                                                             @Parameter(description = "可以传入的值：earliest, latest, popular")
                                                             String orderType,
                                                             @RequestParam(name = "depth", required = false) @Min(1) @Max(3) Integer depth)
    {
        if (!EnumFieldChecker.containsFieldValue(CommentOrderType.class, "value", orderType))
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        return ResponseVO.success(videoCommentService.getCommentList(videoId,
                                                                     parentCommentId,
                                                                     pageNo,
                                                                     orderType,
                                                                     depth == null ? webConfig.getCommentSelectDepth() : depth));
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
