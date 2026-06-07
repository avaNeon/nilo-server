package com.neon.nilocommon.entity.tmp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VideoSeriesVideoCountTMP
{
    /**
     * 系列ID
     */
    private Long seriesId;

    /**
     * 系列下的视频总数
     */
    private Integer videoCount;
}
