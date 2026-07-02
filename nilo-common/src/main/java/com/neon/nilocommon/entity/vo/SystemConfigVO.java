package com.neon.nilocommon.entity.vo;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigVO
{
    // 文件大小限制

    /**
     * 视频大小上限，单位:MB
     */
    private Integer videoFileMaxSize;

    /**
     * 图片大小上限，单位:MB
     */
    private Integer imageMaxSize;

    /**
     * 每日用户上传视频大小限额，单位：MB
     */
    private Integer dailyVideoUploadSize;

    /**
     * 每日用户上传图片大小限额，单位：MB
     */
    private Integer dailyImageUploadSize;


    // 视频格式限制

    /**
     * 最大分辨率支持
     */
    private String maxResolutionRatio;

    /**
     * 最大码率支持，单位:fps
     */
    private Integer maxBitRate;


    // 其余数量限制

    /**
     * 单个视频最大分P数
     */
    private Integer videoMaxEpisodes;

    /**
     * 最大系列视频数量
     */
    private Short maxSerieVideosNumber;

    /**
     * 最大系列数量
     */
    private short maxSeriesNumber;


    // 硬币奖励与消耗

    /**
     * 每个上传的视频奖励硬币数
     */
    private Integer rewardsPreUpload;

    /**
     * 修改昵称硬币花费
     */
    private Short modifyNickNameCost;
}
