package com.neon.nilocomment.controller;

import com.neon.nilocomment.config.CommentConfig;
import com.neon.nilocomment.service.VideoCommentService;
import com.neon.nilocommon.annotation.RedisAuthorized;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoComment.CommentOrderType;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.comment.VideoCommentVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.redisauth.RedisLoginState;
import com.neon.nilocommon.util.EnumFieldChecker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "视频评论接口", description = "视频评论相关接口")
@RequiredArgsConstructor
@RequestMapping("/comment")
@RestController
public class VideoCommentController
{
    private final VideoCommentService videoCommentService;

    private final RedisTemplate <String, Object> redisTemplate;

    private final CommentConfig commentConfig;

    private final RedisLoginState loginState;

    @Operation(summary = "发布视频评论", description = "发布视频评论接口，登录状态通过token传递")
    @RedisAuthorized
    @PostMapping("/comment")
    public ResponseVO <Long> postComment(@RequestHeader(name = "token") String token,
                                         @RequestParam(name = "videoId") @NotNull Long videoId,
                                         @RequestParam(name = "content", required = false) @Size(max = 1000) String content,
                                         @RequestParam(name = "imgPaths", required = false) @Size(max = 150) String imgPaths,
                                         @RequestParam(name = "repliedCommentId", required = false) Long repliedCommentId)
    {
        long userId = loginState.getLoginUserId(token);

        boolean hasContent = content != null && !content.isBlank();
        boolean hasImg = imgPaths != null && !imgPaths.isBlank();

        // 如果既没有内容也没图片就是空白评论，不合法
        if (!hasContent && !hasImg)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }

        return ResponseVO.success(videoCommentService.postComment(userId,
                                                                  videoId,
                                                                  content,
                                                                  imgPaths,
                                                                  repliedCommentId == null ? 0 : repliedCommentId));
    }

    @Operation(summary = "获取视频评论列表",
               description = "获取视频评论列表接口，登录状态通过token传递，页大小为10，子评论页大小为5，深度默认为3")
    @GetMapping(path = "/comment")
    public ResponseVO <List <VideoCommentVO>> getCommentList(@RequestHeader(name = "token", required = false) String token,
                                                             @RequestParam(name = "videoId") @NotNull Long videoId,
                                                             @RequestParam(name = "parentCommentId") @NotNull
                                                             Long parentCommentId,
                                                             @RequestParam(name = "pageNo") @NotNull @Positive Integer pageNo,
                                                             @RequestParam(name = "orderType") @NotEmpty
                                                             @Parameter(description = "可以传入的值：earliest, latest, popular")
                                                             String orderType,
                                                             @RequestParam(name = "depth", required = false) @Min(1) @Max(3)
                                                             Integer depth)
    {
        if (!EnumFieldChecker.containsFieldValue(CommentOrderType.class, "value", orderType))
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        // 因为不传userId也可以，所以这里不用 getLoginState 方法
        TokenUserInfo tokenUserInfo = (TokenUserInfo) redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        return ResponseVO.success(videoCommentService.getCommentList(tokenUserInfo == null ? null : tokenUserInfo.getUserInfo()
                                                                                                                 .getUserId(),
                                                                     videoId,
                                                                     parentCommentId,
                                                                     pageNo,
                                                                     orderType,
                                                                     depth == null ? commentConfig.getCommentSelectDepth() : depth));
    }

    @Operation(summary = "逻辑删除评论",
               description = "逻辑删除一条评论（删除位标记为1），登录状态通过token传递，只有评论发布者和视频发布者可以删除评论")
    @RedisAuthorized
    @DeleteMapping(path = "/comment")
    public ResponseVO <Object> deleteComment(@RequestHeader(name = "token") String token,
                                             @RequestParam(name = "commentId") @NotNull Long commentId)
    {
        long userId = loginState.getLoginUserId(token);
        videoCommentService.deleteComment(userId, commentId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "获取顶层评论数量", description = "获取视频的第一层评论数量）")
    @GetMapping(path = "/count")
    public ResponseVO <Integer> getFirstLevelCommentCount(@RequestParam(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoCommentService.getFirstLevelCommentCount(videoId));
    }

    @Operation(summary = "置顶评论", description = "置顶一条评论，可以置顶多条评论，后发布的评论会排在前面")
    @RedisAuthorized
    @PostMapping(path = "/top")
    public ResponseVO <Object> topComment(@RequestHeader(name = "token") String token,
                                          @RequestParam(name = "commentId") @NotNull Long commentId)
    {
        long userId = loginState.getLoginUserId(token);
        videoCommentService.topComment(userId, commentId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "取消置顶评论", description = "取消置顶一条评论")
    @RedisAuthorized
    @DeleteMapping(path = "/top")
    public ResponseVO <Object> cancelTopComment(@RequestHeader(name = "token") String token,
                                                @RequestParam(name = "commentId") @NotNull Long commentId)
    {
        long userId = loginState.getLoginUserId(token);
        videoCommentService.cancelTopComment(userId, commentId);
        return ResponseVO.success(null);
    }
}
