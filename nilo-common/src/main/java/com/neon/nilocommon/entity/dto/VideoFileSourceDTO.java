package com.neon.nilocommon.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已发布视频的一个分P，供 AI 服务读字幕、切块灌入向量库
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoFileSourceDTO
{
    private Long videoId;

    private String videoName;

    private Long fileId;

    /**
     * 第几P，从 1 开始
     */
    private Integer fileIndex;

    /**
     * MinIO 里的目录，已发布的文件在 public/{filePath}/ 下
     */
    private String filePath;

    /**
     * 时长，单位：秒
     */
    private Integer duration;
}
