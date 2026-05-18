package com.neon.niloweb.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 系统配置
 */
@ConfigurationProperties(prefix = "system")
@Setter
@Getter
@Configuration
@Validated
public class SystemConfig
{
    /**
     * 视频文件大小上限，单位:MB
     */
    @Min(0)
    private Integer videoFileMaxSize = 64;

    /**
     * 图片文件大小上限，单位:MB
     */
    @Min(0)
    private Integer imageMaxSize = 10;

    /**
     * 单个视频最大分P数
     */
    @Min(1)
    private Integer videoMaxEpisodes = 100;

    /**
     * 注册用户初始赠送硬币数
     */
    @Min(0)
    private Integer registerCoin = 10;

    /**
     * 每个上传的视频奖励硬币数
     */
    @Min(0)
    private Integer rewardsPreUpload = 5;

    /**
     * 视频在线人数统计，心跳过期时间，单位:ms<hr/>
     * 默认10s内的数据算作有效数据
     */
    @Min(1)
    private Integer onlineExpireTimeMs = 10_000;

    /**
     * 统计视频在线人数的ZSET的清理周期，单位:ms<hr/>
     * 默认30s清理一次旧数据
     */
    @Min(1)
    private Integer onlineCountCleanUpTimeMs = 30_000;

    /**
     * 最大内容时长，单位:min
     */
    @Min(1)
    private Integer maxPartitionDuration = 60;

    /**
     * 最大分辨率支持
     */
    private String maxResolutionRatio = "1280×720";

    /**
     * 最大码率支持，单位:fps
     */
    @Min(1)
    private Integer maxBitRate = 60;

    /**
     * 修改昵称硬币花费
     */
    @Min(1)
    private Short modifyNickNameCost = 1;

}
