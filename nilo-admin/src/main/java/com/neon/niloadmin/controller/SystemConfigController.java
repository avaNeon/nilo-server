package com.neon.niloadmin.controller;

import com.neon.nilocommon.config.SystemConfig;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统配置管理")
@RequiredArgsConstructor
@Validated
@RequestMapping(path = "/system")
@RestController
public class SystemConfigController
{
    private final SystemConfigRedisRepository systemConfigRedisRepository;

    @Operation(summary = "获取系统配置")
    @GetMapping(path = "/config")
    public ResponseVO <SystemConfig> getSystemConfig()
    {
        return ResponseVO.success(systemConfigRedisRepository.getSystemConfig());
    }

    @Operation(summary = "修改系统配置")
    @PutMapping(path = "/config")
    public ResponseVO <Object> updateSystemConfig(@RequestBody @Valid SystemConfig systemConfig)
    {
        systemConfigRedisRepository.saveSystemConfig(systemConfig);
        return ResponseVO.success(null);
    }
}
