package com.neon.nilocommon.entity.dto.mq;

import com.neon.nilocommon.entity.enums.videoIndex.VideoIndexTaskType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 通知 nilo-ai 重建或删除某个视频的向量索引
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VideoIndexTaskDTO
{
    private Long videoId;

    private VideoIndexTaskType videoIndexTaskType;
}
