package com.neon.nilocommon.entity.enums.statisticsInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DataType
{
    FOLLOWER((short) 1),
    PLAY((short) 2),
    COMMENT((short) 3),
    DANMAKU((short) 4),
    LIKE((short) 5),
    COLLECT((short) 6),
    COIN((short) 7);

    private final short value;
}
