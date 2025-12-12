package com.neon.nilocommon.entity.po;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * 分类信息
 */
@Schema(description = "分类信息")
@Setter
@Getter
public class CategoryInfo implements Serializable
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
     * 排序号
     */
    @Schema(description = "排序序号")
    private Integer sort;

    @Schema(hidden = true)
    private List <CategoryInfo> children;

    @Override
    public String toString()
    {
        return "自增分类ID:" + (categoryId == null ? "空" : categoryId) + "，分类编码:" + (categoryNumber == null ? "空" : categoryNumber) + "，分类名称:" + (categoryName == null ? "空" : categoryName) + "，父级分类ID:" + (pCategoryId == null ? "空" : pCategoryId) + "，图标:" + (icon == null ? "空" : icon) + "，背景图:" + (background == null ? "空" : background) + "，排序号:" + (sort == null ? "空" : sort);
    }
}
