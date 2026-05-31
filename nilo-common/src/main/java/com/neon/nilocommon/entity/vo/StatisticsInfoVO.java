package com.neon.nilocommon.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsInfoVO
{
    /**
     * 统计日期
     */
    private LocalDate statisticsDate;

    /**
     * 数据统计类型
     */
    private Short dataType;

    /**
     * 统计数量
     */
    private Integer statisticsCount;
}
