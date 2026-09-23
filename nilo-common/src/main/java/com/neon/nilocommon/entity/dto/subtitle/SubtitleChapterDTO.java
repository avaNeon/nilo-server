package com.neon.nilocommon.entity.dto.subtitle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 视频里的一个章节，点了可以跳到对应时间
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SubtitleChapterDTO
{
    /**
     * 章节开始时间，单位：秒，从这一P开头算起
     */
    private Integer startSec;

    private String title;
}
