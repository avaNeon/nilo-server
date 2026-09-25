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
     * 同步视频标题（web 端 video_info.video_name 变更时调用，与 web 端方法处于同一个 Seata AT 全局事务中）
     */
    Integer updateVideoNameByVideoId(@Param("videoId") long videoId, @Param("videoName") String videoName);

    /**
     * 同步视频封面（web 端 video_info.video_cover 变更时调用，与 web 端方法处于同一个 Seata AT 全局事务中）
     */
    Integer updateVideoCoverByVideoId(@Param("videoId") long videoId, @Param("videoCover") String videoCover);
}
