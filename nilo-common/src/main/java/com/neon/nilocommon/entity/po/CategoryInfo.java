package com.neon.nilocommon.entity.po;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 分类信息
 */
@Schema(description = "分类信息")
@Setter
@Getter
public class CategoryInfo
{
    /**
     * 自增分类ID<hr/>
     * 避免暴露给用户，可以暴露给后台管理系统
     */
    @Schema(description = "自增分类ID")
    private Integer categoryId;

    /**
     * 分类编码<hr/>
     * 业务主键，有语义，方便在未来的场景标识数据唯一性
     */
    @Schema(description = "分类编码")
    @NotEmpty
    private String categoryNumber;

    /**
     * 分类名称
     */
    @Schema(description = "分类名称")
    @NotEmpty
    private String categoryName;

    /**
     * 父级分类ID
     */
    @Schema(description = "父级分类ID")
    @NotNull
    @JsonProperty("pCategoryId")
    @JsonAlias({"PCategoryId", "pcategoryId"})
    private Integer pCategoryId;

    /**
     * 图标
     */
    @Schema(description = "图标")
    private String icon;

    /**
     * 背景图
     */
    @Schema(description = "背景图")
    private String background;

    /**
     * 主题色
     */
    @Schema(description = "主题色")
    private String color;

    /**
     * 排序号
     */
    @Schema(description = "排序序号")
    private Integer sort;

    @Schema(hidden = true)
    private List <CategoryInfo> children;
}
