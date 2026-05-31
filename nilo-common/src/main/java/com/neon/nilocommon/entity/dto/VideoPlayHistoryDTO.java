package com.neon.nilocommon.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoPlayHistoryDTO
{
    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 文件索引
     */
    private Integer fileIndex;

    /**
     * 最后更新时间
     */
    private LocalDate lastUpdateTime;

    /**
     * 视频封面
     */
    private String videoCover;

    /**
     * 视频名称
     */
    private String videoName;
}
