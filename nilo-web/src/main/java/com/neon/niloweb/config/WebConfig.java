package com.neon.niloweb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 项目配置
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "project")
public class WebConfig
{
    /**
     * 【必填】项目文件夹根目录
     */
    private String rootFilePath;

    /**
     * 查询视频分页大小
     */
    private int pageSize = 20;

    /**
     * 用户主页合集展示条数
     */
    private int userHomeSeriesDisplaySize = 10;

    /**
     * 用户主页合集视频展示条数
     */
    private int userHomeSeriesVideoDisplaySize = 5;

    /**
     * 查询评论分页大小
     */
    private int commentPageSize = 10;

    /**
     * 子评论分页大小（超出则由前端显示"查看更多回复"）
     */
    private int childrenCommentPageSize = 5;

    /**
     * 默认查询评论深度
     */
    private int commentSelectDepth = 3;

    /**
     * 用户信息过期时间，单位：天
     */
    private int userInfoExpireDays = 7;
}
