package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.query.BaseQuery;
import org.apache.ibatis.annotations.Param;

/**
 * 视频信息 数据库操作接口
 */
public interface VideoInfoMapper<T, P  > extends BaseMapper <T, P>
{

    /**
     * 根据VideoId更新
     */
    Integer updateByVideoId(@Param("bean") T t, @Param("videoId") Long videoId);


    /**
     * 根据VideoId删除
     */
    Integer deleteByVideoId(@Param("videoId") Long videoId);


    /**
     * 根据VideoId获取对象
     */
    T selectByVideoId(@Param("videoId") Long videoId);


}
