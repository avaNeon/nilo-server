package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一个分P的 AI 总结，内容来自转码时生成的 summary.json
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoFileSummary
{
    /**
     * 第几P，从 1 开始。summary.json 里没有这个字段，读出来后补上
     */
    private Integer fileIndex;

    private String summary;

    /**
     * 章节时间从这一P开头算起
     */
    private List <VideoChapter> chapters;
}
