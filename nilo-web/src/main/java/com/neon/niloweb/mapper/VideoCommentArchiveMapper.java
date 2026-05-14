package com.neon.niloweb.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 评论存档 数据库操作接口
 */
public interface VideoCommentArchiveMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 根据CommentId更新
     */
    Integer updateByCommentId(@Param("bean") T t, @Param("commentId") Long commentId);


    /**
     * 根据CommentId删除
     */
    Integer deleteByCommentId(@Param("commentId") Long commentId);


    /**
     * 根据CommentId获取对象
     */
    T selectByCommentId(@Param("commentId") Long commentId);


}
