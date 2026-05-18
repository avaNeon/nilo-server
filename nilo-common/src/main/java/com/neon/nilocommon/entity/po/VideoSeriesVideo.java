package com.neon.nilocommon.entity.po;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * 用户视频合集视频信息
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VideoSeriesVideo
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
