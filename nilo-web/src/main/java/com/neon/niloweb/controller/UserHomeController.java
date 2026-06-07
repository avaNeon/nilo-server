package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.UpdatedUserInfoDTO;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.TokenUserInfoVO;
import com.neon.nilocommon.entity.vo.UserDetailVO;
import com.neon.nilocommon.entity.vo.videoInfo.BriefVideoInfoVO;
import com.neon.nilocommon.entity.vo.videoInfo.CollectedVideoInfoVO;
import com.neon.nilocommon.entity.vo.videoSeriesInfo.VideoSeriesWithVideosVO;
import com.neon.nilocommon.loginState.LoginState;
import com.neon.nilocommon.util.ServletUtil;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.service.UserHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "用户中心接口", description = "用户中心操作")
@Validated
@RequiredArgsConstructor
@RequestMapping("/uHome")
@RestController
public class UserHomeController
{
    private final UserHomeService userHomeService;

    private final RedisTemplate <String, Object> redisTemplate;

    private final LoginState loginState;

    private final WebConfig webConfig;

    @Operation(summary = "获取用户主页信息")
    @GetMapping(path = "/user/{hostUserId}")
    public ResponseVO <UserDetailVO> getUserDetail(@RequestHeader(name = "token", required = false) String token,
                                                   @PathVariable(name = "hostUserId") @NotNull Long hostUserId)
    {
        Long visitorUserId = null;
        if (token != null)
        {
            TokenUserInfo tokenUserInfo = (TokenUserInfo) redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
            if (tokenUserInfo != null && tokenUserInfo.getUserInfo() != null && tokenUserInfo.getUserInfo().getUserId() != null)
            {
                visitorUserId = tokenUserInfo.getUserInfo().getUserId();
            }
        }
        return ResponseVO.success(userHomeService.getUserDetail(visitorUserId, hostUserId));
    }

    @Operation(summary = "更新用户信息", description = "如果更新了昵称或者头像，会返回新的token")
    @PostMapping(path = "/user")
    public ResponseVO <TokenUserInfoVO> updateUserInfo(@Parameter(hidden = true) HttpServletRequest request,
                                                       @Parameter(hidden = true) HttpServletResponse response,
                                                       @RequestHeader(name = "token") @NotEmpty String token,
                                                       @RequestBody @NotNull @Validated UpdatedUserInfoDTO updatedUserInfoDTO)
    {
        TokenUserInfo loginState = this.loginState.getLoginState(token);
        // 检查下 userId 是否存在
        this.loginState.getLoginUserId(token);
        TokenUserInfoVO tokenUserInfoVO = userHomeService.updateUserInfo(loginState, updatedUserInfoDTO);
        if (tokenUserInfoVO != null)
        {
            ServletUtil.removeCookie(request, response, Constants.WEB_COOKIE_TOKEN_KEY);
            ServletUtil.setCookie(response,
                                  Constants.WEB_COOKIE_TOKEN_KEY,
                                  tokenUserInfoVO.getToken(),
                                  webConfig.getUserInfoExpireDays(),
                                  TimeUnit.DAYS);
        }
        return ResponseVO.success(tokenUserInfoVO);
    }

    @Operation(summary = "修改个人主页主题")
    @PostMapping(path = "/theme/{themeIndex}")
    public ResponseVO <Object> saveTheme(@RequestHeader(name = "token") @NotEmpty String token,
                                         @PathVariable(name = "themeIndex") @NotNull Short themeIndex)
    {
        long userId = loginState.getLoginUserId(token);
        userHomeService.saveTheme(userId, themeIndex);
        return ResponseVO.success(null);
    }

    @Operation(summary = "查询投稿视频列表", description = "查询视频列表，按照创建时间倒序排序")
    @GetMapping(path = "/video/{userId}")
    public ResponseVO <PaginationResponseVO <BriefVideoInfoVO>> loadVideo(@PathVariable(name = "userId") @NotNull Long userId,
                                                                          @RequestParam(name = "pageNo") @NotNull @Min(1)
                                                                          Integer pageNo,
                                                                          @RequestParam(name = "pageSize") @NotNull @Min(1)
                                                                          @Max(20) Integer pageSize,
                                                                          @RequestParam(name = "sortType") @NotNull @Min(1)
                                                                          @Max(3) Short sortType,
                                                                          @RequestParam(name = "keyword", required = false)
                                                                          @Size(max = 100) String keyword)
    {
        return ResponseVO.success(userHomeService.loadVideo(userId, pageNo, pageSize, sortType, keyword));
    }

    @Operation(summary = "查询收藏视频列表", description = "查询收藏视频列表，按照创建时间倒序排序")
    @GetMapping(path = "/collection/{userId}")
    public ResponseVO <PaginationResponseVO <CollectedVideoInfoVO>> loadCollection(
            @PathVariable(name = "userId") @NotNull Long userId, @RequestParam(name = "pageNo") @NotNull @Min(1) Integer pageNo)
    {
        return ResponseVO.success(userHomeService.loadCollection(userId, pageNo));
    }

    @Operation(summary = "获取用户系列展示")
    @GetMapping("/series/videos/{userId}")
    public ResponseVO <List <VideoSeriesWithVideosVO>> loadSeriesWithVideos(@PathVariable(name = "userId") @NotNull Long userId)
    {
        return ResponseVO.success(userHomeService.loadSeriesWithVideos(userId));
    }
}
