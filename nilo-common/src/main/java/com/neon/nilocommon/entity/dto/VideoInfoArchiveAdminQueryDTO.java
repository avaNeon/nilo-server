package com.neon.nilocommon.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Schema(description = "后台视频存档列表查询参数")
@Getter
@Setter
public class VideoInfoArchiveAdminQueryDTO
{
    @NotNull
    @Min(1)
    private Integer pageNo;

    @Max(20)
    @Min(1)
    private Integer pageSize;

    private Long videoId;

    private String videoNameFuzzy;

    private Long userId;

    private Integer deleterType;

    private Integer pCategoryId;

    private Integer categoryId;

    private Short postType;

    private String tagsFuzzy;

    private String introductionFuzzy;

    private String interaction;
}
