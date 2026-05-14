package com.neon.niloadmin.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 用户评论行为 点赞、点踩 数据库操作接口
 */
public interface UserCommentActionMapper<T, P> extends BaseMapper <T, P>
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
     * 根据 commentId 和 userId 查询
     *
     */
    T selectByCommentIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /**
     * 根据CommentIdAndUserIdAndActionType获取对象
     */
    T selectByCommentIdAndUserIdAndActionType(@Param("commentId") Long commentId,
                                              @Param("userId") Long userId,
                                              @Param("actionType") Integer actionType);

    /**
     * 根据CommentIdAndUserIdAndActionType更新
     */
    Integer updateByCommentIdAndUserIdAndActionType(@Param("bean") T t,
                                                    @Param("commentId") Long commentId,
                                                    @Param("userId") Long userId,
                                                    @Param("actionType") Integer actionType);


    /**
     * 根据CommentIdAndUserIdAndActionType删除
     */
    Integer deleteByCommentIdAndUserIdAndActionType(@Param("commentId") Long commentId,
                                                    @Param("userId") Long userId,
                                                    @Param("actionType") Integer actionType);

}
