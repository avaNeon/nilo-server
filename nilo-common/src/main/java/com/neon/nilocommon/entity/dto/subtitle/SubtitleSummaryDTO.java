package com.neon.nilocommon.entity.dto.subtitle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 大模型根据字幕生成的视频总结，转码时算好存成 summary.json，前端直接读
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SubtitleSummaryDTO
{
    /**
     * 几句话的中文总结
     */
    private String summary;

    /**
     * 按时间顺序排好的章节，视频太短或分不出来时是空的
     */
    private List <SubtitleChapterDTO> chapters;
}
