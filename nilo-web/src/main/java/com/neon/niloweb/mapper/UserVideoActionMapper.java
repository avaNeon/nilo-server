package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.po.UserVideoAction;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户视频行为 点赞、收藏、投币 数据库操作接口
 */
public interface UserVideoActionMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 根据用户ID和操作类型查询记录数量
     *
     * @param userId     用户ID
     * @param actionType 操作类型
     * @return 记录数量
     */
    Integer selectCountByUserIdAndActionType(@Param("userId") Long userId, @Param("actionType") Short actionType);

    /**
     * 根据用户ID和操作类型查询记录
     *
     * @param userId     用户ID
     * @param actionType 操作类型
     * @return 记录数量
     */
    List <UserVideoAction> selectByUserIdAndActionType(@Param("userId") Long userId, @Param("actionType") Short actionType);

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
