package com.neon.nilocommon.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadingVideoFile
{
    private Long uploadId;
    private String fileName;
    private Integer chunkIndex;
    private Integer chunkSize;
    private Long fileSize = 0L;
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
