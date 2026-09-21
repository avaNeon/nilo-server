package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频检索命中<hr/>
 * searchVideo 工具返回给模型的一条结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoHitVO
{
    private Long videoId;

    private String videoName;

    /**
     * 检索原文（标题、标签、简介），过长时截断
     */
    private String text;
}
