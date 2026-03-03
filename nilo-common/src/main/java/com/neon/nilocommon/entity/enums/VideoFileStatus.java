package com.neon.nilocommon.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum VideoFileStatus
{
    TRANSCODING((short) 0, "转码中"), TRANSCODING_FAIL((short) 1, "转码成功"), PENDING_REVIEW((short) 2, "转码失败");

    private final Short status;

    private final String description;
}
