package com.neon.niloadmin.config;

import com.neon.nilocommon.entity.po.Admin;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@ConfigurationProperties(prefix = "project")
@Configuration
public class AdminConfig
{
    private Admin[] admins;

    private String rootFilePath;

    private boolean showCommandLogs;

    private short coinBonusPerVideo = 10;

    /**
     * 最大推荐视频数量
     */
    private short maxRecommendVideoNumber = 10;
}
