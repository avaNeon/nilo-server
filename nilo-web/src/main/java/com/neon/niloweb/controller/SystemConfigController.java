package com.neon.niloweb.controller;

import cn.hutool.core.bean.BeanUtil;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.SystemConfigVO;
import com.neon.niloweb.config.SystemConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 获取系统配置
 */
@Tag(name = "系统配置")
@RequestMapping("/system")
@RequiredArgsConstructor
@RestController
public class SystemConfigController
{
    private final SystemConfig systemConfig;

    @Operation(summary = "获取系统配置")
    @GetMapping("/config")
    public ResponseVO <SystemConfigVO> getSystemConfig()
    {
        SystemConfigVO systemConfigVO = new SystemConfigVO();
        BeanUtil.copyProperties(systemConfig, systemConfigVO);
        return ResponseVO.success(systemConfigVO);
    }
}
