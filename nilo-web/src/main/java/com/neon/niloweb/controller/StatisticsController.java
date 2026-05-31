package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.StatisticsInfoVO;
import com.neon.nilocommon.loginState.LoginState;
import com.neon.niloweb.annotation.Authorized;
import com.neon.niloweb.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "统计管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/statistics")
public class StatisticsController
{
    private final StatisticsService statisticsService;

    private final LoginState loginState;

    @Operation(summary = "获取近期统计数据")
    @GetMapping("/recent")
    @Authorized
    public ResponseVO <List <StatisticsInfoVO>> loadRecentStatisticsInfo(@RequestHeader(name = "token") @NotNull String token)
    {
        long userId = loginState.getLoginUserId(token);

        List <StatisticsInfoVO> recentStatisticsInfoList = statisticsService.getRecentStatisticsInfo(userId);

        return ResponseVO.success(recentStatisticsInfoList);
    }
}
