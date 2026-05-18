package com.neon.nilocommon.entity.vo.videoSeriesInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VideoSeriesInfoVO
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

    private String videoCover;
}
