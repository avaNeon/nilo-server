package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.dto.VideoPlayHistoryDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.annotation.Authorized;
import com.neon.niloweb.service.VideoPlayHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "播放历史管理")
@RequiredArgsConstructor
@Validated
@RestController
@RequestMapping("/history")
public class VideoPlayHistoryController
{
    private final VideoPlayHistoryService videoPlayHistoryService;

    private final LoginState loginState;

    @Operation(summary = "记录视频播放历史")
    @PostMapping("/{videoId}/{fileIndex}")
    @Authorized
    public ResponseVO <Object> saveHistory(@RequestHeader(name = "token") @NotEmpty String token,
                                           @PathVariable(name = "videoId") @NotNull @Positive Long videoId,
                                           @PathVariable(name = "fileIndex") @NotNull @Min(1) Integer fileIndex)
    {
        videoPlayHistoryService.saveHistory(loginState.getLoginUserId(token), videoId, fileIndex);

        return ResponseVO.success(null);
    }

    @Operation(summary = "分页查询播放历史", description = "有可能出现没有视频信息的情况")
    @GetMapping("/{pageNo}")
    @Authorized
    public ResponseVO <List <VideoPlayHistoryDTO>> getHistoryList(@RequestHeader(name = "token") @NotEmpty String token,
                                                                  @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo)
    {
        List <VideoPlayHistoryDTO> historyList = videoPlayHistoryService.getHistoryList(loginState.getLoginUserId(token), pageNo);
        return ResponseVO.success(historyList);
    }

    @Operation(summary = "删除单条播放历史")
    @DeleteMapping("/{videoId}")
    @Authorized
    public ResponseVO <Object> deleteHistory(@RequestHeader(name = "token") @NotEmpty String token,
                                             @PathVariable(name = "videoId") @NotNull @Positive Long videoId)
    {
        videoPlayHistoryService.deleteHistory(loginState.getLoginUserId(token), videoId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "删除全部播放历史")
    @DeleteMapping("/all")
    @Authorized
    public ResponseVO <Object> deleteAllHistory(@RequestHeader(name = "token") @NotEmpty String token)
    {
        videoPlayHistoryService.deleteAllHistory(loginState.getLoginUserId(token));
        return ResponseVO.success(null);
    }
}
