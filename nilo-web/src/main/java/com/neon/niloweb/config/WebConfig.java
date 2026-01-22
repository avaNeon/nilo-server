package com.neon.niloweb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
public class WebConfig
{
    /**
     * 项目文件夹根目录
     */
    @Value("${project.folder}")
    private String rootFilePath;

    @Value("${log.command}")
    private boolean showCommandLogs;
}
