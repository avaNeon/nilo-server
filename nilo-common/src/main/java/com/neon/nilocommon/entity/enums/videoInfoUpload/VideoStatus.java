package com.neon.nilocommon.entity.enums.videoInfoUpload;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum VideoStatus
{
    TRANSCODING((short) 0, "转码中"),
    TRANSCODING_FAIL((short) 1, "转码失败"),
    PENDING_REVIEW((short) 2, "待审核"),
    REVIEW_SUCCESS((short) 3, "审核成功"),
    REVIEW_FAILED((short) 4, "审核未通过");

    private final Short status;

    private final String description;
}
