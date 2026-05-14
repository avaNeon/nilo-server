package com.neon.nilocommon.entity.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VideoInfoFileUploadVO
{
    /**
     * 上传ID
     */
    private Long uploadId;

    /**
     * 文件名
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
     * 0:转码中 1:转码成功 2:转码失败
     */
    private Short transferResult;
}
