package com.neon.niloweb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 系统配置
 */
@ConfigurationProperties(prefix = "system")
@Setter
@Getter
@Configuration
public class SystemConfig
{
    /**
     * 视频大小上限，单位:MB
     */
    private Integer videoMaxSize = 64;

    /**
     * 单个视频最大分P数
     */
    private Integer videoMaxEpisodes = 100;

    /**
     * 注册用户初始赠送硬币数
     */
    private Integer registerCoin = 10;

    /**
     * 每个上传的视频奖励硬币数
     */
    private Integer rewardsPreUpload = 5;

}
