package com.neon.niloweb.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 用户视频行为 点赞、收藏、投币 数据库操作接口
 */
public interface UserVideoActionArchiveMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 根据ActionId更新
     */
    Integer updateByActionId(@Param("bean") T t, @Param("actionId") Long actionId);


    /**
     * 根据ActionId删除
     */
    Integer deleteByActionId(@Param("actionId") Long actionId);


    /**
     * 根据ActionId获取对象
     */
    T selectByActionId(@Param("actionId") Long actionId);


}
