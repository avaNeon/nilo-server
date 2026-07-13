package com.neon.nilocommon.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频快照（跨服务精简字段，供评论等场景使用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoSnapshotDTO
{
    private Long videoId;

    private Long userId;

    private String videoName;

    private String videoCover;

    private String interaction;
}
