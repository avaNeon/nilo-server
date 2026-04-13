package com.neon.nilocommon.entity.enums.userVideoAction;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VideoActionType
{
    LIKE(1), SAVE(2), COIN(3);

    private final int value;
}
