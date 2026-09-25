package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 今天的提问额度
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AskQuotaVO
{
    /**
     * 今天已经提问的次数
     */
    private int used;

    /**
     * 每天最多提问次数
     */
    private int limit;
}
