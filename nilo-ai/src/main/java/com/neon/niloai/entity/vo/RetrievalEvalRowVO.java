package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalEvalRowVO
{
    private String question;

    private String type;

    private List <Long> expectedVideoIds;

    /**
     * 召回的前 5 条，带原始文本
     */
    private List <RetrievedDocVO> retrieved;

    /**
     * 期望视频是否出现在前 5
     */
    private Boolean hit;

    /**
     * 期望视频是否排第一
     */
    private Boolean hitAt1;

    /**
     * 第一个期望 videoId 的名次，1 开始；前 5 没有则为 null
     */
    private Integer rank;
}
