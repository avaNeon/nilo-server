package com.neon.niloadmin.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 视频弹幕 数据库操作接口
 */
public interface VideoDanmakuMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 根据DanmakuId更新
     */
    Integer updateByDanmakuId(@Param("bean") T t, @Param("danmakuId") Long danmakuId);


    /**
     * 根据DanmakuId删除
     */
    Integer deleteByDanmakuId(@Param("danmakuId") Long danmakuId);


    /**
     * 根据DanmakuId获取对象
     */
    T selectByDanmakuId(@Param("danmakuId") Long danmakuId);


}
