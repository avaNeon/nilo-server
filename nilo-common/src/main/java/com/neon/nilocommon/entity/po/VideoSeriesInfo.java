package com.neon.nilocommon.entity.po;

import lombok.*;

import java.time.LocalDateTime;


/**
 * 用户视频合集信息
 */
@ToString
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VideoSeriesInfo
{
    /**
     * 合集ID
     */
    private Long seriesId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 合集名称
     */
    private String seriesName;

    /**
     * 合集描述
     */
    private String seriesDescription;

    /**
     * 排序序号
     */
    private Integer sortIndex;

    private LocalDateTime updateTime;
}
