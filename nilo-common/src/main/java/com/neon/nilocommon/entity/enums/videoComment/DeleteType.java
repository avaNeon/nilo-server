package com.neon.nilocommon.entity.enums.videoComment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeleteType
{
    UNDELETED(0), DELETED_BY_USER(1), DELETED_BY_VIDEO_CREATER(2), DELETED_BY_ADMIN(3);

    private final int value;
}
