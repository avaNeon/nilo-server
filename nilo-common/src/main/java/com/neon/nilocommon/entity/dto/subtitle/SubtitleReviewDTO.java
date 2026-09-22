package com.neon.nilocommon.entity.dto.subtitle;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 大模型质检字幕的结果
 */
@Getter
@Setter
@NoArgsConstructor
public class SubtitleReviewDTO
{
    /**
     * 读得出意思的句子占全部句子的百分比，0 到 100
     */
    private Integer meaningfulPercent;
}
