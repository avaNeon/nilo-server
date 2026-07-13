package com.neon.nilocomment.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 用户评论行为存档 点赞、点踩 数据库操作接口
 */
public interface UserCommentActionArchiveMapper<T, P> extends BaseMapper <T, P>
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
