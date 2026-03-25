package com.neon.nilocommon.entity.enums.videoInfoFileUpload;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum VideoFileStatus
{
    TRANSCODING((short) 0, "转码中"), TRANSCODING_SUCCESS((short) 1, "转码成功"), TRANSCODING_FAIL((short) 2, "转码失败");

    private final Short status;

    private final String description;
}
