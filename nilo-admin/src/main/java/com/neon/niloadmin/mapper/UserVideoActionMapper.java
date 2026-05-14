package com.neon.niloadmin.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户视频行为 点赞、收藏、投币 数据库操作接口
 */
public interface UserVideoActionMapper<T, P> extends BaseMapper <T, P>
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


    /**
     * 根据VideoIdAndActionTypeAndUserId更新
     */
    Integer updateByVideoIdAndActionTypeAndUserId(@Param("bean") T t,
                                                  @Param("videoId") Long videoId,
                                                  @Param("actionType") Integer actionType,
                                                  @Param("userId") Long userId);


    /**
     * 根据VideoIdAndActionTypeAndUserId删除
     */
    Integer deleteByVideoIdAndActionTypeAndUserId(@Param("videoId") Long videoId,
                                                  @Param("actionType") Integer actionType,
                                                  @Param("userId") Long userId);


    /**
     * 根据VideoIdAndActionTypeAndUserId获取对象
     */
    T selectByVideoIdAndActionTypeAndUserId(@Param("videoId") Long videoId,
                                            @Param("actionType") Integer actionType,
                                            @Param("userId") Long userId);

    /**
     * 根据VideoIdAndActionTypeAndUserId获取对象
     */
    List <T> selectByVideoIdAndUserId(@Param("videoId") Long videoId, @Param("userId") Long userId);
}
