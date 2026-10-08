package com.neon.nilocomment.mapper;

import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import org.apache.ibatis.annotations.Param;

/**
 * 视频资料快照
 */
public interface VideoInfoReplicaMapper
{
    /**
     * 按视频ID读取快照，不存在时返回 null
     */
    VideoSnapshotDTO selectByVideoId(@Param("videoId") long videoId);

    /**
     * 新增或覆盖视频快照（canal 监听到 video_info 新增、更新后触发），重复执行结果相同
     */
    Integer upsert(@Param("video") VideoSnapshotDTO video);

    /**
     * 删除视频快照（视频删除时触发），行不存在时什么都不做
     */
    Integer deleteByVideoId(@Param("videoId") long videoId);
}
