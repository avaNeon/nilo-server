package com.neon.niloadmin.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 视频信息 数据库操作接口
 */
public interface VideoInfoMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 将指定字段增加一定的量
     *
     * @param filed     指定字段
     * @param increment 增量
     * @return 修改行数
     */
    Integer increaseByField(@Param("videoId") Long videoId, @Param("field") String filed, @Param("increment") Integer increment);

    /**
     * 将指定字段减少一定的量
     * @param videoId 视频ID
     * @param filed 字段名
     * @param decrement 减量
     * @return 修改行数
     */
    Integer decreaseByField(@Param("videoId") Long videoId, @Param("field") String filed, @Param("decrement") Integer decrement);

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
