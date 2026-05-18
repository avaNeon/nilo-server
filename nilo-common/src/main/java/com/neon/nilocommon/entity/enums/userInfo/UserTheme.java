package com.neon.nilocommon.entity.enums.userInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public enum UserTheme
{
    FIRST((short) 1),
    SECOND((short) 2),
    THIRD((short) 3),
    FOURTH((short) 4),
    FIFTH((short) 5),
    SIXTH((short) 6),
    SEVENTH((short) 7),
    EIGHTH((short) 8),
    NINTH((short) 9),
    TENTH((short) 10);

    public final short value;
}
