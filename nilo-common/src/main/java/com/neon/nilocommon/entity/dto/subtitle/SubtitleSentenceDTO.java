package com.neon.nilocommon.entity.dto.subtitle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 发给大模型翻译的一句台词，模型按同样的格式返回译文
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SubtitleSentenceDTO
{
    /**
     * 句子编号，从 1 开始。模型原样返回，用来把译文对回原句
     */
    private Integer id;

    private String text;
}
