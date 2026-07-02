package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.VideoDanmakuService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.danmaku.DanmakuManagementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "视频弹幕管理")
@RequiredArgsConstructor
@Validated
@RequestMapping(path = "/danmaku")
@RestController
public class VideoDanmakuController
{
    private final VideoDanmakuService videoDanmakuService;

    @Operation(summary = "获取弹幕数量")
    @GetMapping
    public ResponseVO <Long> getDanmakuManagementInfoCount(@RequestParam(name = "nameFuzzy", required = false) String nameFuzzy)
    {
        return ResponseVO.success(videoDanmakuService.getDanmakuManagementInfoCount(nameFuzzy));
    }

    @Operation(summary = "获取弹幕")
    @GetMapping(path = "/{pageNo}/{pageSize}")
    public ResponseVO <List <DanmakuManagementVO>> getDanmakuManagementInfo(
            @RequestParam(name = "nameFuzzy", required = false) String nameFuzzy,
            @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo,
            @PathVariable(name = "pageSize") @Min(1) @Max(10) @NotNull Integer pageSize)
    {
        return ResponseVO.success(videoDanmakuService.getDanmakuManagementInfo(nameFuzzy, pageNo, pageSize));
    }

    @Operation(summary = "删除视频弹幕")
    @DeleteMapping(path = "/{danmakuId}")
    public ResponseVO <Object> deleteDanmaku(@PathVariable(name = "danmakuId") @NotNull Long danmakuId)
    {
        videoDanmakuService.deleteDanmaku(danmakuId);
        return ResponseVO.success(null);
    }
}
