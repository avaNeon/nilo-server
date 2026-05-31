package com.neon.nilocommon.entity.enums.userMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MessageType
{
    SYSTEM((short) 1), LIKE((short) 2), COLLECT((short) 3), COMMENT((short) 4);

    private final short value;
}
