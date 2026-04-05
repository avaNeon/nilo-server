package com.neon.nilocommon.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 分类信息（对外展示，不暴露内部ID）
 */
@Schema(description = "分类信息VO")
@Getter
@Setter
public class CategoryInfoVO
{
    /**
     * 分类编码，业务主键
     */
    @Schema(description = "分类编码")
    private String categoryNumber;

    /**
     * 分类名称
     */
    @Schema(description = "分类名称")
    private String categoryName;

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
     * 排序序号
     */
    @Schema(description = "排序序号")
    private Integer sort;

    /**
     * 子分类
     */
    @Schema(description = "子分类")
    private List<CategoryInfoVO> children;
}

