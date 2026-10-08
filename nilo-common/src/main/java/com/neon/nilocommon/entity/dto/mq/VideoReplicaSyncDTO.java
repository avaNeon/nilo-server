package com.neon.nilocommon.entity.dto.mq;

import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import com.neon.nilocommon.entity.enums.comment.ReplicaSyncType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 通知评论服务同步视频副本<hr/>
 * 新增和删除走同一个队列，保证同一个视频的操作按发生顺序执行（例如删除后马上恢复）
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class VideoReplicaSyncDTO
{
    private ReplicaSyncType type;

    /**
     * 删除时只用到 videoId
     */
    private VideoSnapshotDTO video;
}
