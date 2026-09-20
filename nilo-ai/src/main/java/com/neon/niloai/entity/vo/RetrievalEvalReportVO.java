package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalEvalReportVO
{
    private Integer total;

    private Integer hits;

    private Double hitRate;

    /**
     * 正确答案排第一的题数
     */
    private Integer hitsAt1;

    private Double hitAt1Rate;

    /**
     * 平均倒数排名，整体口径
     */
    private Double mrr;

    /**
     * 按 exact / semantic 分开的统计，对比两版时主要看这里
     */
    private List <RetrievalEvalGroupVO> groups;

    private List <RetrievalEvalRowVO> rows;
}
