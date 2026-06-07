package com.neon.nilocommon.entity.enums.videoInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SortType
{
    NEWEST((short) 1, "v.last_update_time DESC"),
    MOST_PLAYED((short) 2, "v.play_count DESC"),
    MOST_COLLECTED((short) 3, "v.collect_count DESC");

    private final short no;
    private final String value;
}
