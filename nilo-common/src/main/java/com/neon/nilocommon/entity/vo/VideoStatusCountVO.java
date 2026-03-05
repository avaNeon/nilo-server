package com.neon.nilocommon.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VideoStatusCountVO
{
    Integer pendingCount;
    Integer completedCount;
    Integer failedCount;
}
