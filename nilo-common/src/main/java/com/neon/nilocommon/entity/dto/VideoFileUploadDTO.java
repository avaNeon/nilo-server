package com.neon.nilocommon.entity.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class VideoFileUploadDTO
{
    private Long fileId;

    private String key;

    @NotEmpty(message = "文件名不能为空")
    private String filename;
}
