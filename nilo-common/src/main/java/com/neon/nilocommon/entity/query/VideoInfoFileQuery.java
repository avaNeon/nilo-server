package com.neon.nilocommon.entity.query;


import lombok.Getter;
import lombok.Setter;

/**
 * 视频文件信息参数
 */
@Setter
@Getter
public class VideoInfoFileQuery extends BaseQuery
{

    /**
     * 唯一ID
     */
    private String fileId;

    private String fileIdFuzzy;

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

    private String fileNameFuzzy;

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

    private String filePathFuzzy;

    /**
     * 持续时间（秒）
     */
    private Integer duration;


}
