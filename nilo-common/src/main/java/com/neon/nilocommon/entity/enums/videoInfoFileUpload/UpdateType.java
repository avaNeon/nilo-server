package com.neon.nilocommon.entity.enums.videoInfoFileUpload;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UpdateType
{
    NO_UPDATE((short) 0), UPDATED((short) 1);

    private final short updateType;
}
