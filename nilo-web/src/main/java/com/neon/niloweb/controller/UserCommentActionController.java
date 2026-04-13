package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.UserCommentActionVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.service.UserCommentActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户评论操作接口", description = "用户对评论进行点赞、踩等操作的接口")
@RequiredArgsConstructor
@RequestMapping("/user/commentAction")
@RestController
public class UserCommentActionController
{
    private final RedisTemplate <String, Object> redisTemplate;

    private final UserCommentActionService userCommentActionService;

    @Operation(summary = "评论操作接口", description = "用户对评论进行点赞、点踩")
    @PostMapping("/action")
    public ResponseVO <Object> commentAction(@RequestHeader(name = "token") String token,
                                             @RequestParam(name = "commentId") @NotNull Long commentId,
                                             @RequestParam(name = "actionType") @NotNull Integer actionType)
    {
        TokenUserInfo loginState = getLoginState(token);
        userCommentActionService.commentAction(loginState.getUserInfo().getUserId(), commentId, actionType);
        return ResponseVO.success(null);
    }

    @Operation(summary = "获取评论操作状态接口", description = "获取用户对评论的操作状态（是否点赞、是否点踩）")
    @GetMapping("/action")
    public ResponseVO <List <UserCommentActionVO>> getCommentAction(@RequestHeader(name = "token") String token,
                                                                    @RequestParam(name = "commentId") @NotNull Long commentId)
    {
        TokenUserInfo loginState = getLoginState(token);
        return ResponseVO.success(userCommentActionService.getCommentAction(loginState.getUserInfo().getUserId(), commentId));
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
