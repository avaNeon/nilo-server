package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.userInfo.BriefUserInfoVO;
import com.neon.nilocommon.loginState.LoginState;
import com.neon.niloweb.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "关注接口", description = "与关注有关的操作")
@Validated
@RequiredArgsConstructor
@RequestMapping("/follow")
@RestController
public class FollowController
{
    private final FollowService followService;

    private final LoginState loginState;

    @Operation(summary = "关注/取消关注")
    @PostMapping(path = "/follow/{followingUserId}")
    public ResponseVO <Object> follow(@RequestHeader(name = "token") @NotEmpty String token,
                                      @PathVariable(name = "followingUserId") @NotNull Long followingUserId)
    {
        long followerUserId = loginState.getLoginUserId(token);
        followService.followOperation(followerUserId, followingUserId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "获取粉丝列表")
    @GetMapping(path = "/follower/{pageNo}/{pageSize}")
    public ResponseVO <List <BriefUserInfoVO>> getFollowerList(@RequestHeader(name = "token") @NotEmpty String token,
                                                               @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo,
                                                               @PathVariable(name = "pageSize") @NotNull @Min(1) @Max(10)
                                                               Integer pageSize)
    {
        long followingUserId = loginState.getLoginUserId(token);
        return ResponseVO.success(followService.getFollowerList(followingUserId, pageNo, pageSize));
    }

    @Operation(summary = "获取关注列表")
    @GetMapping(path = "/following/{pageNo}/{pageSize}")
    public ResponseVO <List <BriefUserInfoVO>> getFollowingList(@RequestHeader(name = "token") @NotEmpty String token,
                                                                @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo,
                                                                @PathVariable(name = "pageSize") @NotNull @Min(1) @Max(10)
                                                                Integer pageSize)

    {
        long followerUserId = loginState.getLoginUserId(token);
        return ResponseVO.success(followService.getFollowingList(followerUserId, pageNo, pageSize));
    }

//    @Operation(summary = "获取用户粉丝数")
//    @GetMapping("/user/{userId}")
//    public ResponseVO <Integer> getFollowerCount(@PathVariable(name = "userId") @NotNull Long userId)
//    {
//        return ResponseVO.success(followService.getFollowerCount(userId));
//    }

}
