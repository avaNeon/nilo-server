package com.neon.nilocommon.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 表示VideoInfoFileUpload的每一块
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadedVideoFileDTO
{
    private Long uploadId;
    private String fileName;
    /**
     * 目前保存到第几块
     */
    private Integer chunkIndex;
    private Integer chunkSize;
    private Long fileSize = 0L;
    /**
     * 文件的相对路径，环境目录为根目录（即配置文件的project.folder）+ file + tmp/video等等...<br/>
     * 相对路径是这样的：date/userId/uploadId
     */
    private String filePath;

    /**
     * 使fileSize+=fileIncrement
     * @param fileIncrement 文件增加了多少
     */
    public void addFileSize(Long fileIncrement)
    {
        fileSize += fileIncrement;
    }
}
