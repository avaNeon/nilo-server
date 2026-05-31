package com.neon.nilocommon.entity.query;

import lombok.Getter;
import lombok.Setter;


/**
 * 数据统计参数
 */
@Setter
@Getter
public class StatisticsInfoQuery extends BaseQuery
{
    /**
     * 统计日期
     */
    private String statisticsDate;

    private String statisticsDateStart;

    private String statisticsDateEnd;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 数据统计类型
     */
    private Integer dataType;

    /**
     * 统计数量
     */
    private Integer statisticsCount;
}
