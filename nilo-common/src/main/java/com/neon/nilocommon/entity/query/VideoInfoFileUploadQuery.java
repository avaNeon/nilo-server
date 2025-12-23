package com.neon.nilocommon.entity.query;


import lombok.Getter;
import lombok.Setter;

/**
 * 视频文件信息参数
 */
@Setter
@Getter
public class VideoInfoFileUploadQuery extends BaseQuery
{


    /**
     * 唯一ID
     */
    private String fileId;

    private String fileIdFuzzy;

    /**
     * 上传ID
     */
    private String uploadId;

    private String uploadIdFuzzy;

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

    private String fileNameFuzzy;

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
     * 0:无更新 1:有更新
     */
    private Integer updateType;

    /**
     * 0:转码中 1:转码成功 2:转码失败
     */
    private Integer transferResult;

    /**
     * 持续时间（秒）
     */
    private Integer duration;


}
