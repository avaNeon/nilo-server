package com.neon.nilocommon.entity.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class VideoInfoFileVO
{
    /**
     * 视频文件名
     */
    private String fileName;

    /**
     * 文件索引
     */
    private Integer fileIndex;

    /**
     * 持续时间（秒）
     */
    private Integer duration;
}
