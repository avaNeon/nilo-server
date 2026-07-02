package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;


/**
 * 视频文件信息
 */
@Setter
@Getter
public class VideoInfoFileUpload
{
    /**
     * 唯一ID
     */
    private Long fileId;

    /**
     * 上传ID
     */
    private Long uploadId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 文件索引
     */
    private Integer fileIndex;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件路径（只保存相对路径）
     */
    private String filePath;

    /**
     * 0:无更新 1:有更新
     */
    private Short updateType;

    /**
     * 0:转码中 1:转码成功 2:转码失败
     */
    private Short transferResult;

    /**
     * 持续时间（秒）
     */
    private Integer duration;

}
