package com.neon.nilocommon.entity.enums.videoInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RecommendType
{
    UNRECOMMENDED(0), RECOMMENDED(1);

    private final int type;
}
