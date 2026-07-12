package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;


/**
 * 视频文件信息
 */
@Setter
@Getter
public class VideoInfoFile
{
    /**
     * 唯一ID
     */
    private Long fileId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 视频文件名
     */
    private String fileName;

    /**
     * 文件索引
     */
    private Integer fileIndex;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 持续时间（秒）
     */
    private Integer duration;

}
