package com.neon.nilocommon.entity.query;

import lombok.Getter;
import lombok.Setter;


/**
 * 用户视频合集信息参数
 */
@Setter
@Getter
public class VideoSeriesInfoQuery extends BaseQuery
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

    private String seriesNameFuzzy;

    /**
     * 合集描述
     */
    private String seriesDescription;

    private String seriesDescriptionFuzzy;

    /**
     * 排序序号
     */
    private Integer sortIndex;

    /**
     * 更新时间
     */
    private String updateTime;

    private String updateTimeStart;

    private String updateTimeEnd;
}
