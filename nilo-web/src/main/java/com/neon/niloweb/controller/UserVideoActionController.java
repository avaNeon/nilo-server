package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.UserVideoActionVO;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.service.UserVideoActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户视频操作接口", description = "用户对视频进行点赞、投币、收藏等操作的接口")
@Validated
@RequiredArgsConstructor
@RequestMapping("/user/videoAction")
@RestController
public class UserVideoActionController
{
    private final LoginState loginState;

    private final UserVideoActionService userVideoActionService;

    @Operation(summary = "视频操作接口", description = "用户对视频进行点赞、投币、收藏等操作")
    @PostMapping("/action")
    public ResponseVO <Object> videoAction(@RequestHeader(name = "token") String token,
                                           @RequestParam(name = "videoId") @NotNull Long videoId,
                                           @RequestParam(name = "actionType") @NotNull Short actionType,
                                           @RequestParam(name = "coinAmount", required = false) @Min(1) @Max(2) Short coinAmount)
    {
        long userId = loginState.getLoginUserId(token);

        userVideoActionService.videoAction(userId, videoId, actionType, coinAmount == null ? (short) 0 : coinAmount);

        return ResponseVO.success(null);
    }

    @Operation(summary = "获取视频操作状态接口", description = "获取用户对视频的操作状态（是否点赞、是否收藏、投币数量）")
    @GetMapping("/action")
    public ResponseVO <List <UserVideoActionVO>> getVideoAction(@RequestHeader(name = "token") String token,
                                                                @RequestParam(name = "videoId") @NotNull Long videoId)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(userVideoActionService.getVideoAction(userId, videoId));
    }
}
