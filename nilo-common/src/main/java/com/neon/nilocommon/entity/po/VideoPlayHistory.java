package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;


/**
 * 视频播放历史
 */
@Setter
@Getter
@ToString
public class VideoPlayHistory
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
    private LocalDate lastUpdateTime;
}
