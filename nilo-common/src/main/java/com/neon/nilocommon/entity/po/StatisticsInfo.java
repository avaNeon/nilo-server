package com.neon.nilocommon.entity.po;

import lombok.*;

import java.time.LocalDate;


/**
 * 数据统计
 */
@Setter
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsInfo
{
    /**
     * 统计日期
     */
    private LocalDate statisticsDate;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 数据统计类型
     */
    private Short dataType;

    /**
     * 统计数量
     */
    private Integer statisticsCount;
}
