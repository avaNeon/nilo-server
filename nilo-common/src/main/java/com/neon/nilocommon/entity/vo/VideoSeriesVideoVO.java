package com.neon.nilocommon.entity.vo;

import com.neon.nilocommon.entity.vo.videoInfo.BasicVideoInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VideoSeriesVideoVO extends BasicVideoInfo
{
    /**
     * 合集ID
     */
    private Long seriesId;
}
