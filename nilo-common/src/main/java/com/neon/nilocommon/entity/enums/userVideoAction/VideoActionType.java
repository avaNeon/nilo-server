package com.neon.nilocommon.entity.enums.userVideoAction;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VideoActionType
{
    LIKE((short) 1), SAVE((short) 2), COIN((short) 3);

    private final short value;
}
