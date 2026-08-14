package com.neon.nilocomment.controller;

import com.neon.nilocomment.service.UserCommentActionService;
import com.neon.nilocommon.annotation.RedisAuthorized;
import com.neon.nilocommon.annotation.RateLimit;
import com.neon.nilocommon.entity.enums.RateLimitType;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.redisauth.RedisLoginState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户评论操作接口", description = "用户对评论进行点赞、踩等操作的接口")
@RequiredArgsConstructor
@RequestMapping("/user/commentAction")
@RateLimit(by = RateLimitType.USER)
@RestController
public class UserCommentActionController
{
    private final RedisLoginState loginState;

    private final UserCommentActionService userCommentActionService;

    @Operation(summary = "评论操作接口", description = "用户对评论进行点赞、点踩")
    @RedisAuthorized
    @PostMapping("/action")
    public ResponseVO <Void> commentAction(@RequestHeader(name = "token") String token,
                                           @RequestParam(name = "videoId") @NotNull Long videoId,
                                           @RequestParam(name = "commentId") @NotNull Long commentId,
                                           @RequestParam(name = "actionType") @NotNull Integer actionType)
    {
        long userId = loginState.getLoginUserId(token);
        userCommentActionService.commentAction(userId, videoId, commentId, actionType);
        return ResponseVO.success();
    }
}
