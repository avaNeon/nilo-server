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

    private List <RetrievalEvalRowVO> rows;
}
