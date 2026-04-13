package com.neon.nilocommon.entity.enums.videoInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InteractionType
{
    NO_DANMAKU("0"), NO_COMMENT("1"), NO_DANMAKU_AND_COMMENT("0,1");

    private final String value;
}
