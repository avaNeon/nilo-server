package com.neon.niloweb.controller;

import cn.hutool.core.bean.BeanUtil;
import com.neon.nilocommon.annotation.RateLimit;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.SystemConfigVO;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
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
@RateLimit
@RequiredArgsConstructor
@RestController
public class SystemConfigController
{
    private final SystemConfigRedisRepository systemConfigRedisRepository;

    @Operation(summary = "获取系统配置")
    @GetMapping("/config")
    public ResponseVO <SystemConfigVO> getSystemConfig()
    {
        // 就两步就完事，没必要写service层
        SystemConfigVO systemConfigVO = new SystemConfigVO();
        BeanUtil.copyProperties(systemConfigRedisRepository.getSystemConfig(), systemConfigVO);

        return ResponseVO.success(systemConfigVO);
    }
}
