package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * 分类信息
 */
@Setter
@Getter
public class CategoryInfo implements Serializable
{
    /**
     * 自增分类ID<hr/>
     * 避免暴露给用户，可以暴露给后台管理系统
     */
    private Integer categoryId;

    /**
     * 分类编码<hr/>
     * 业务主键，有语义，方便在未来的场景标识数据唯一性
     */
    private String categoryNumber;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 父级分类ID
     */
    private Integer pCategoryId;

    /**
     * 图标
     */
    private String icon;

    /**
     * 背景图
     */
    private String background;

    /**
     * 排序号
     */
    private Integer sort;

    private List <CategoryInfo> children;


    @Override
    public String toString()
    {
        return "自增分类ID:" + (categoryId == null ? "空" : categoryId) + "，分类编码:" + (categoryNumber == null ? "空" : categoryNumber) + "，分类名称:" + (categoryName == null ? "空" : categoryName) + "，父级分类ID:" + (pCategoryId == null ? "空" : pCategoryId) + "，图标:" + (icon == null ? "空" : icon) + "，背景图:" + (background == null ? "空" : background) + "，排序号:" + (sort == null ? "空" : sort);
    }
}
