package com.neon.nilocommon.entity.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoFileUploadVO
{
    @NotNull(message = "uploadId不能为空")
    private Long uploadId;

    @NotEmpty(message = "文件名不能为空")
    private String filename;
}
