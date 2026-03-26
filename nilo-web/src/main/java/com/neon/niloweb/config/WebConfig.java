package com.neon.niloweb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 项目配置
 */
@Getter
@Setter
@Configuration
public class WebConfig
{
    /**
     * 【必填】项目文件夹根目录
     */
    @Value("${project.folder}")
    private String rootFilePath;

    /**
     * 查询视频分页大小
     */
    @Value("${project.pageSize}")
    private final int pageSize = 20;
}
