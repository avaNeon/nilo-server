package com.neon.nilocommon.entity.query;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用PO查询类模板，包含分页和排序功能
 */
@Schema(description = "基本分页")
@Setter
@Getter
public class BaseQuery
{
    /**
     * 分页计算工具，在需要计算LIMIT时使用
     */
    @Schema(hidden = true)
    private PageCalculator pageCalculator;
    /**
     * 页号（从1开始）
     */
    @Schema(defaultValue = "1")
    private Integer pageNo;
    @Schema(defaultValue = "20")
    private Integer pageSize;
    @Schema(hidden = true)
    private String orderBy;
}
