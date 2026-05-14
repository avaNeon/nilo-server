package com.neon.nilocommon.entity.enums.videoInfoArchive;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeleterType
{
    USER(0), ADMIN(1);

    private final int value;
}
