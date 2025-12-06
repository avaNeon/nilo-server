package com.neon.nilocommon.entity.query;


import lombok.Getter;
import lombok.Setter;

/**
 * 通用PO查询类模板，包含分页和排序功能
 */
@Setter
@Getter
public class BaseQuery
{
    /**
     * 分页计算工具，在需要计算LIMIT时使用
     */
    private PageCalculator pageCalculator;
    /**
     * 页号（从1开始）
     */
    private Integer pageNo;
    private Integer pageSize;
    private String orderBy;
}
