package com.neon.nilocommon.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "视频上传/修改信息DTO")
public class VideoUploadDTO
{
    /**
     * 视频的唯一ID，用于区分视频
     */
    @Schema(description = "视频的唯一ID (修改时必填，新增时留空)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long videoId;

    /**
     * 封面文件key
     */
    @Schema(description = "封面文件key", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "封面不能为空")
    private String coverPath;

    /**
     * 视频标题
     */
    @Schema(description = "视频标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "视频标题不能为空")
    @Size(max = 100, message = "视频标题不能超过100个字符")
    private String videoTitle;

    /**
     * 所属分类编码 (业务主键，对应CategoryInfo.categoryNumber)
     */
    @Schema(description = "所属分类编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "分类不能为空")
    private String categoryNumber;

    /**
     * 自制/转载
     */
    @Schema(description = "投稿类型 (1:自制 2:转载)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "投稿类型不能为空")
    private Short postType;

    /**
     * 标签
     */
    @Schema(description = "标签 (多个标签用逗号分隔)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 300, message = "标签内容过长")
    private String tags;

    /**
     * 视频简介
     */
    @Schema(description = "视频简介", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 2000, message = "视频简介不能超过2000个字符")
    private String introduction;

    /**
     * 原资源说明
     */
    @Schema(description = "原资源说明", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String originInfo;

    /**
     * 互动设置
     */
    @Schema(description = "互动设置", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 5)
    private String interaction;

    /**
     * 视频文件列表
     */
    @Schema(description = "视频文件列表，旧文件只传fileId和文件名；新文件只传key和文件名", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotEmpty(message = "没有视频文件")
    private List <VideoFileUploadDTO> videoFileUploadList;
}
