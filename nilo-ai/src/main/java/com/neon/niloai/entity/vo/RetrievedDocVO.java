package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测时召回的一条结果，带上向量化之前的原始文本，便于人工判断为什么会被召回
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedDocVO
{
    private Long videoId;

    /**
     * 入库前拼好的那段文本（标题/标签/简介）
     */
    private String text;
}
