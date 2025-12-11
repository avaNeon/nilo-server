package com.neon.nilocommon.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Schema(description = "分类信息")
@Getter
@Setter
public class CategoryDTO
{
    @Schema(description = "自增分类ID")
    private Integer categoryId;

    @Schema(description = "分类编码")
    @NotEmpty
    private String categoryNumber;

    @Schema(description = "分类名称")
    @NotEmpty
    private String categoryName;

    @Schema(description = "父级分类ID")
    @NotNull
    private Integer pCategoryId;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "背景图")
    private String background;

    private List<CategoryDTO> children;
}
