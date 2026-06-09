package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.GlobalStatisticsService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.StatisticsInfoVO;
import com.neon.nilocommon.entity.vo.UserStatVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "全局统计管理")
@RequiredArgsConstructor
@Validated
@RequestMapping(path = "/glob-stat")
@RestController
public class GlobalStatisticsController
{
    private final GlobalStatisticsService globalStatisticsService;

    @Operation(summary = "获取指定时间内的注册用户数据")
    @GetMapping("/user")
    public ResponseVO <List <UserStatVO>> getUserStatByDatePeriod(
            @RequestParam(name = "start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(name = "end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end)
    {
        return ResponseVO.success(globalStatisticsService.getUserStatByDatePeriod(start, end));
    }

    @Operation(summary = "获取近期统计数据")
    @GetMapping("/recent")
    public ResponseVO <List <StatisticsInfoVO>> loadRecentStatisticsInfo()
    {

        List <StatisticsInfoVO> recentStatisticsInfoList = globalStatisticsService.getRecentStatisticsInfo();

        return ResponseVO.success(recentStatisticsInfoList);
    }
}
