package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.UserCommentActionVO;
import com.neon.nilocommon.loginState.LoginState;
import com.neon.niloweb.service.UserCommentActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户评论操作接口", description = "用户对评论进行点赞、踩等操作的接口")
@RequiredArgsConstructor
@RequestMapping("/user/commentAction")
@RestController
public class UserCommentActionController
{
    private final LoginState loginState;

    private final UserCommentActionService userCommentActionService;

    @Operation(summary = "评论操作接口", description = "用户对评论进行点赞、点踩")
    @PostMapping("/action")
    public ResponseVO <Object> commentAction(@RequestHeader(name = "token") String token,
                                             @RequestParam(name = "videoId") @NotNull Long videoId,
                                             @RequestParam(name = "commentId") @NotNull Long commentId,
                                             @RequestParam(name = "actionType") @NotNull Integer actionType)
    {
        long userId = loginState.getLoginUserId(token);
        userCommentActionService.commentAction(userId, videoId, commentId, actionType);
        return ResponseVO.success(null);
    }

    @Operation(summary = "获取评论操作状态接口", description = "获取用户对评论的操作状态（是否点赞、是否点踩）")
    @GetMapping("/action")
    public ResponseVO <List <UserCommentActionVO>> getCommentAction(@RequestHeader(name = "token") String token,
                                                                    @RequestParam(name = "commentId") @NotNull Long commentId)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(userCommentActionService.getCommentAction(userId, commentId));
    }
}
