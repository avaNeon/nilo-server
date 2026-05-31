package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.dto.VideoPlayHistoryDTO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频播放历史 数据库操作接口
 */
public interface VideoPlayHistoryMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * <b>记录播放历史</b><hr/>
     * <p>如果userId和videoId相同的记录存在，则更新旧记录，而不是插入新记录</p>
     *
     * @param userId         用户ID
     * @param videoId        视频ID
     * @param fileIndex      文件索引
     * @param lastUpdateTime 最新更新时间
     * @return 改变行数
     */
    Integer recordVideoPlayHistory(@Param("userId") Long userId,
                                   @Param("videoId") Long videoId,
                                   @Param("fileIndex") int fileIndex,
                                   @Param("lastUpdateTime") LocalDateTime lastUpdateTime);

    List <VideoPlayHistoryDTO> selectDtoList(@Param("query") P p);

    /**
     * 根据用户ID删除全部播放历史
     */
    Integer deleteByUserId(@Param("userId") Long userId);

    /**
     * 根据UserIdAndVideoId更新
     */
    Integer updateByUserIdAndVideoId(@Param("bean") T t, @Param("userId") Long userId, @Param("videoId") Long videoId);


    /**
     * 根据UserIdAndVideoId删除
     */
    Integer deleteByUserIdAndVideoId(@Param("userId") Long userId, @Param("videoId") Long videoId);


    /**
     * 根据UserIdAndVideoId获取对象
     */
    T selectByUserIdAndVideoId(@Param("userId") Long userId, @Param("videoId") Long videoId);


}
