package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回答所引用的视频片段，前端据此跳到对应分P的对应时间
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitedSegmentVO
{
    private Long videoId;

    private String videoName;

    /**
     * 第几P，从 1 开始
     */
    private Integer fileIndex;

    /**
     * 从这一P开头算起的秒数
     */
    private Integer startSec;
}
