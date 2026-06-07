package com.neon.nilocommon.entity.enums.videoSearch;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderType
{
    COMPREHENSIVE((short) 1), NEWEST((short) 2), MOST_PLAYED((short) 3), MOST_COLLECTED((short) 4), MOST_DANMAKU((short) 5);

    final short value;
}
