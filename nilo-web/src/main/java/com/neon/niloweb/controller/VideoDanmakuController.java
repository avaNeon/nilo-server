package com.neon.niloweb.controller;

import com.neon.nilocommon.annotation.RateLimit;
import com.neon.nilocommon.entity.enums.RateLimitType;
import com.neon.nilocommon.entity.dto.DanmakuDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.danmaku.DanmakuVO;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.service.VideoDanmakuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "视频弹幕接口", description = "提供视频弹幕相关的API")
@AllArgsConstructor
@RequestMapping("/danmaku")
@Validated
@RestController
public class VideoDanmakuController
{
    private final VideoDanmakuService videoDanmakuService;

    private final LoginState loginState;

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "发布弹幕", description = "用户发布视频弹幕")
    @PostMapping(path = "/danmaku")
    public ResponseVO <Object> postDanmaku(@RequestHeader(name = "token") String token,
                                           @RequestBody @NotNull @Valid DanmakuDTO danmakuDTO)
    {
        long userId = loginState.getLoginUserId(token);
        videoDanmakuService.postDanmaku(userId, danmakuDTO);
        return ResponseVO.success(null);
    }

    @RateLimit
    @Operation(summary = "加载弹幕",
               description = "按视频时间轴增量加载弹幕。区间为左闭右开 [fromMs, toMs)，单次跨度不得超过 5000 毫秒")
    @GetMapping(path = "/{videoId}")
    public ResponseVO <List <DanmakuVO>> loadDanmaku(@PathVariable("videoId") Long videoId,
                                                     @RequestParam(name = "fileIndex") @NotNull Integer fileIndex,
                                                     @RequestParam(name = "fromMs") @NotNull @Min(0) Integer fromMs,
                                                     @RequestParam(name = "toMs") @NotNull @Min(0) Integer toMs)
    {
        return ResponseVO.success(videoDanmakuService.loadDanmaku(videoId, fileIndex, fromMs, toMs));
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "删除弹幕", description = "用户只能删除自己的弹幕")
    @DeleteMapping(path = "/danmaku/{danmakuId}")
    public ResponseVO <Object> deleteDanmaku(@RequestHeader(name = "token") @NotEmpty String token,
                                             @PathVariable("danmakuId") @NotNull Long danmakuId)
    {
        long userId = loginState.getLoginUserId(token);
        videoDanmakuService.deleteDanmaku(userId, danmakuId);
        return ResponseVO.success(null);
    }
}
