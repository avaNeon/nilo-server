package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.UserVideoActionVO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.service.UserVideoActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate <String, Object> redisTemplate;

    private final UserVideoActionService userVideoActionService;

    @Operation(summary = "视频操作接口", description = "用户对视频进行点赞、投币、收藏等操作")
    @PostMapping("/action")
    public ResponseVO <Object> videoAction(@RequestHeader(name = "token") String token,
                                           @RequestParam(name = "videoId") @NotNull Long videoId,
                                           @RequestParam(name = "actionType") @NotNull Integer actionType,
                                           @RequestParam(name = "coinAmount", required = false) @Min(1) @Max(2) Short coinAmount)
    {
        TokenUserInfo loginState = getLoginState(token);
        userVideoActionService.videoAction(loginState.getUserInfo().getUserId(), videoId, actionType, coinAmount == null ? (short) 0 : coinAmount);
        return ResponseVO.success(null);
    }

    @Operation(summary = "获取视频操作状态接口", description = "获取用户对视频的操作状态（是否点赞、是否收藏、投币数量）")
    @GetMapping("/action")
    public ResponseVO <List <UserVideoActionVO>> getVideoAction(@RequestHeader(name = "token") String token,
                                                                @RequestParam(name = "videoId") @NotNull Long videoId)
    {
        TokenUserInfo loginState = getLoginState(token);
        return ResponseVO.success(userVideoActionService.getVideoAction(loginState.getUserInfo().getUserId(), videoId));
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
