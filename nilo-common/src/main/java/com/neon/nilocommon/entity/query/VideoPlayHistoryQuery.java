package com.neon.nilocommon.entity.query;


import lombok.Getter;
import lombok.Setter;

/**
 * 视频播放历史参数
 */
@Setter
@Getter
public class VideoPlayHistoryQuery extends BaseQuery
{
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 文件索引
     */
    private Integer fileIndex;

    /**
     * 最后更新时间
     */
    private String lastUpdateTime;

    private String lastUpdateTimeStart;

    private String lastUpdateTimeEnd;
}
