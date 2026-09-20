package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按题型（exact / semantic）分开统计。
 *
 * <p>v2 的提升只会出现在 exact 上，semantic 只要不掉就算合格。混在一起算会同时掩盖收益和副作用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalEvalGroupVO
{
    private String type;

    private Integer total;

    private Integer hits;

    private Double hitRate;

    private Integer hitsAt1;

    private Double hitAt1Rate;

    /**
     * 平均倒数排名。命中第 1 名算 1 分，第 2 名 0.5 分，前 5 没中算 0 分。
     * Hit@5 看不出"从第 4 提到第 1"，MRR 看得出，rerank 的效果全靠它。
     */
    private Double mrr;
}
