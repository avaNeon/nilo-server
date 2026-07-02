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
     * 视频详情页推荐视频数量
     */
    private int recommendVideoSize = 10;

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

    /**
     * 热搜榜单显示记录数量
     */
    private int hotKeywordDisplayCount = 10;

    /**
     * 视频播放数统计间隔（单位：毫秒）
     */
    private int playCountRefreshInterval = 5_000;

    /**
     * 心跳发送至MQ的周期，单位：毫秒
     */
    private int heartbeatSendInterval = 3_000;

    /**
     * 视频在线人数统计心跳过期时间，单位：毫秒
     */
    private int onlineExpireTimeMs = 30_000;

    /**
     * 统计视频在线人数的ZSET清理周期，单位：毫秒
     */
    private int onlineCountCleanUpTimeMs = 15_000;

    // ----- 热门视频相关配置 -----

    /**
     * 冷活跃视频统计时间间隔（单位：小时）
     */
    private int coldVideoTimeDistance = 1;

    /**
     * 热门视频统计时间间隔（单位：小时）
     */
    private int hotVideoTimeDistance = 24;

    /**
     * 冷数据播放数更新周期（单位：s）
     */
    private int coldKeyUpdateInterval = 30;

    /**
     * 热数据播放数更新周期（单位：s）
     */
    private int hotKeyUpdateInterval = 60;

    /**
     * 热度提升阈值（单位：播放数）
     */
    private int upgradeThreshold = 1000;

    /**
     * 热度降低阈值（单位：播放数）
     */
    private int downgradeThreshold = 500;

    /**
     * 热门视频查询页大小
     */
    private int hotVideoPageSize = 20;

    /**
     * 消息分页大小
     */
    private int messagePagSize = 10;

    /**
     * 历史记录分页大小
     */
    private int playHistoryPageSize = 20;

}
