package com.neon.nilocommon.entity.vo.videoSeriesInfo;

import com.neon.nilocommon.entity.vo.VideoSeriesVideoVO;
import com.neon.nilocommon.entity.vo.videoInfo.BasicVideoInfo;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class VideoSeriesWithVideosVO
{
    /**
     * 合集ID
     */
    private Long seriesId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 合集名称
     */
    private String seriesName;

    /**
     * 合集描述
     */
    private String seriesDescription;

    /**
     * 排序序号
     */
    private Integer sortIndex;

    private LocalDateTime updateTime;

    private List<VideoSeriesVideoVO> videoInfoList;
}
