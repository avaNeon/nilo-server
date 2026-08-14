package com.neon.niloweb.controller;

import com.neon.nilocommon.annotation.RateLimit;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.service.VideoOnlineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "视频在线管理")
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/online")
@RateLimit
public class VideoOnlineController
{
    private final VideoOnlineService videoOnlineService;

    /**
     * 心跳
     *
     * @param videoId   视频ID
     * @param fileIndex 视频文件序号
     * @param sessionId 会话ID
     */
    @Operation(summary = "视频在线心跳接口", description = "发送心跳信息，使过期时间延长")
    @PostMapping(path = "/heartbeat")
    public ResponseVO <Object> sendHeartbeat(@RequestParam(name = "videoId") @NotNull Long videoId,
                                             @RequestParam(name = "fileIndex") @NotNull Integer fileIndex,
                                             @RequestParam(name = "sessionId") @NotBlank String sessionId)
    {
        videoOnlineService.sendHeartbeat(videoId, fileIndex, sessionId);
        return ResponseVO.success(null);
    }


    /**
     * 查询在线人数
     *
     * @param videoId   视频ID
     * @param fileIndex 视频文件序号
     * @return 在线人数
     */
    @Operation(summary = "查询视频在线人数接口", description = "查询当前正在观看视频的在线人数")
    @GetMapping(path = "/count")
    public ResponseVO <Long> getOnlineCount(@RequestParam(name = "videoId") @NotNull Long videoId,
                                            @RequestParam(name = "fileIndex") @NotNull Integer fileIndex)
    {
        return ResponseVO.success(videoOnlineService.getOnlineCount(videoId, fileIndex));
    }
}
