package com.neon.nilocommon.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供 AI 向量化的视频文本来源
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoEmbedSourceDTO
{
    private Long videoId;

    private String videoName;

    private String tags;

    private String introduction;
}
