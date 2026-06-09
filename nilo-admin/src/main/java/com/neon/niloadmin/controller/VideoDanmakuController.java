package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.VideoDanmakuService;
import com.neon.nilocommon.entity.po.VideoDanmaku;
import com.neon.nilocommon.entity.query.VideoDanmakuQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "获取视频弹幕列表")
    @PostMapping(path = "/list")
    public ResponseVO <List <VideoDanmaku>> loadDanmakuList(@RequestBody VideoDanmakuQuery query)
    {
        return ResponseVO.success(videoDanmakuService.loadDanmakuList(query));
    }

    @Operation(summary = "删除视频弹幕")
    @DeleteMapping(path = "/{danmakuId}")
    public ResponseVO <Object> deleteDanmaku(@PathVariable(name = "danmakuId") @NotNull Long danmakuId)
    {
        videoDanmakuService.deleteDanmaku(danmakuId);
        return ResponseVO.success(null);
    }
}
