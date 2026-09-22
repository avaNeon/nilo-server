package com.neon.nilocommon.entity.dto.subtitle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SRT 字幕里的一条：一段时间和这段时间显示的文字
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SubtitleCueDTO
{
    /**
     * 开始时间，单位：毫秒，从这一P开头算起
     */
    private long startMs;

    private long endMs;

    private String text;
}
