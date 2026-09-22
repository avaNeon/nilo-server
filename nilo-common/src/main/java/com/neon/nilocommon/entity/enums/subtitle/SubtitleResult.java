package com.neon.nilocommon.entity.enums.subtitle;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一个分P生成字幕的结果
 */
@AllArgsConstructor
@Getter
public enum SubtitleResult
{
    NO_SPEECH("没识别出人声，不生成字幕"),
    UNREADABLE("识别结果被判定为乱码，不生成字幕"),
    CHINESE("中文字幕，不需要翻译"),
    TRANSLATED("原文字幕 + 中文翻译"),
    TRANSLATE_FAILED("翻译失败，只有原文字幕");

    private final String description;
}
