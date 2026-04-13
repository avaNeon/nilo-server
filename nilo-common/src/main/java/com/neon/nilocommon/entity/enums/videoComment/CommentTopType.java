package com.neon.nilocommon.entity.enums.videoComment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommentTopType
{
    NOT_TOP((short) 0), TOP((short) 1);

    private final short value;
}
