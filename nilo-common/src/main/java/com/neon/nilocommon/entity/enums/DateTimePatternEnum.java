package com.neon.nilocommon.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 常用时间格式枚举
 */
@Getter
@RequiredArgsConstructor
public enum DateTimePatternEnum
{
    DATE_TIME("yyyy-MM-dd HH:mm:ss"), DATE("yyyy-MM-dd");

    private final String pattern;

}
