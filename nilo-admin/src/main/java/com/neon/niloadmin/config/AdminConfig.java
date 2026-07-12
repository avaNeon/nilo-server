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

    private boolean showCommandLogs;

    /**
     * 最大推荐视频数量
     */
    private short maxRecommendVideoNumber = 11;
}
