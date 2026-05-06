package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.dto.DanmakuDTO;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.DanmakuVO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import com.neon.niloweb.service.VideoDanmakuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    private final AccountRedisRepository accountRedisRepository;

    @Operation(summary = "发布弹幕", description = "用户发布视频弹幕")
    @PostMapping(path = "/danmaku")
    public ResponseVO <Object> postDanmaku(@RequestHeader(name = "token") String token, @RequestBody @NotNull @Valid DanmakuDTO danmakuDTO)
    {

        TokenUserInfo userInfo = accountRedisRepository.getTokenUserInfoByToken(token);
        if (userInfo == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }
        videoDanmakuService.postDanmaku(userInfo.getUserInfo().getUserId(), danmakuDTO);
        return ResponseVO.success(null);
    }

    @Operation(summary = "加载弹幕", description = "加载视频弹幕")
    @GetMapping(path = "/danmaku/{videoId}")
    public ResponseVO <List <DanmakuVO>> loadDanmaku(@PathVariable("videoId") Long videoId,
                                                     @RequestParam(name = "fileIndex") @NotNull Integer fileIndex)
    {
        return ResponseVO.success(videoDanmakuService.loadDanmaku(videoId, fileIndex));
    }
}
