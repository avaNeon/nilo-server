package com.neon.nilocommon.entity.vo;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigVO
{
    /**
     * 视频大小上限，单位:MB
     */
    private Integer videoFileMaxSize;

    /**
     * 图片大小上限，单位:MB
     */
    private Integer imageMaxSize;

    /**
     * 单个视频最大分P数
     */
    private Integer videoMaxEpisodes;

    /**
     * 注册用户初始赠送硬币数
     */
    private Integer registerCoin;

    /**
     * 每个上传的视频奖励硬币数
     */
    private Integer rewardsPreUpload;

    /**
     * 最大内容时长，单位:min
     */
    private Integer maxPartitionDuration;

    /**
     * 最大分辨率支持
     */
    private String maxResolutionRatio;

    /**
     * 最大码率支持，单位:fps
     */
    private Integer maxBitRate;
}
