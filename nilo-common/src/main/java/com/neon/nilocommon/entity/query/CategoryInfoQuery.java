package com.neon.nilocommon.entity.query;


import com.neon.nilocommon.entity.annotation.BlankRestriction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 分类信息参数
 */
@Schema(description = "分类信息分页查询")
@Setter
@Getter
@BlankRestriction
public class CategoryInfoQuery extends BaseQuery
{
    @Schema(description = "自增分类ID")
    private Integer categoryId;

    /**
     * 分类编码
     */
    private String categoryNumber;

    private String categoryNumberFuzzy;

    /**
     * 分类名称
     */
    private String categoryName;

    private String categoryNameFuzzy;

    /**
     * 父级分类ID
     */
    private Integer pCategoryId;

    /**
     * 图标
     */
    private String icon;

    private String iconFuzzy;

    /**
     * 背景图
     */
    private String background;

    private String backgroundFuzzy;

    /**
     * 排序号
     */
    private Integer sort;

}
