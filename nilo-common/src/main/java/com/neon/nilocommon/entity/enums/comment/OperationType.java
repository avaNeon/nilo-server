package com.neon.nilocommon.entity.enums.comment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OperationType
{
    ARCHIVE(0), RECOVERY(1), DESTROY(2);

    private final int value;
}
