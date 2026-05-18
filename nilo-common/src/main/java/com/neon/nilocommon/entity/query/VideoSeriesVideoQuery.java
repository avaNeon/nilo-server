package com.neon.nilocommon.entity.query;


import lombok.Getter;
import lombok.Setter;

/**
 * 用户视频合集视频信息参数
 */
@Setter
@Getter
public class VideoSeriesVideoQuery extends BaseQuery
{
    /**
     * 合集ID
     */
    private Long seriesId;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 排序序号
     */
    private Integer sortIndex;
}
