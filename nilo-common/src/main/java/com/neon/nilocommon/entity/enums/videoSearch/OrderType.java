package com.neon.nilocommon.entity.enums.videoSearch;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderType
{
    NEWEST((short) 1), MOST_PLAYED((short) 2), MOST_COLLECTED((short) 3), MOST_DANMAKU((short) 4);

    final short value;
}
