package com.neon.nilocommon.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 系统配置
 */
@ConfigurationProperties(prefix = "system")
@Setter
@Getter
@Validated
public class SystemConfig
{
    // 文件大小限制

    /**
     * 单个视频文件大小上限，单位:MB
     */
    @Min(0)
    private Integer videoFileMaxSize = 100;

    /**
     * 单个图片文件大小上限，单位:MB
     */
    @Min(0)
    private Integer imageMaxSize = 10;

    /**
     * 每日用户上传视频大小限额，单位：MB
     */
    @Min(0)
    private Integer dailyVideoUploadSize = 100;

    /**
     * 每日用户上传图片大小限额，单位：MB
     */
    @Min(0)
    private Integer dailyImageUploadSize = 50;

    // 视频格式限制

    /**
     * 最大分辨率支持
     */
    private String maxResolutionRatio = "1280×720";

    /**
     * 最大码率支持，单位:fps
     */
    @Min(1)
    private Integer maxBitRate = 60;


    // 其余数量限制

    /**
     * 单个视频最大分P数
     */
    @Min(1)
    private Integer videoMaxEpisodes = 100;

    /**
     * 最大系列视频数量
     */
    @Min(1)
    private Short maxSerieVideosNumber = 100;

    /**
     * 最大系列数量
     */
    @Min(1)
    private short maxSeriesNumber = 100;


    // 硬币奖励与消耗

    /**
     * 注册用户初始赠送硬币数
     */
    @Min(0)
    private Integer registerCoin = 10;

    /**
     * 每个上传的视频奖励硬币数
     */
    @Min(0)
    private Integer rewardsPreUpload = 10;

    /**
     * 修改昵称硬币花费
     */
    @Min(1)
    private Short modifyNickNameCost = 1;
}
