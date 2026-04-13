package com.neon.niloweb.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评论 数据库操作接口
 */
public interface VideoCommentMapper<T, P> extends BaseMapper <T, P>
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

    /**
     * 批量查询父评论的子评论（per-parent top-K）。<br/>
     * 使用 ROW_NUMBER() OVER (PARTITION BY parent_comment_id) 保证每个父节点最多返回 limitPerParent 条，
     * 结果按 post_time ASC 排序。<br/>
     * 是否还有更多子评论由调用方通过 {@code replyCount > limitPerParent} 判断，无需 K+1 探查。
     *
     * @param parentIdList   父节点 comment_id 列表
     * @param videoId        视频 ID
     * @param limitPerParent 每个父节点最多返回的子评论数
     * @return 子评论列表，每父最多 limitPerParent 条
     */
    List <T> selectByParentIdList(@Param("parentIdList") List <Long> parentIdList,
                                  @Param("videoId") long videoId,
                                  @Param("limitPerParent") int limitPerParent);

    /**
     * 将指定评论的直接子评论计数 reply_count +1。<br/>
     * 在 postComment 发布回复时调用（parentCommentId != 0 的情况）。
     *
     * @param commentId 被回复的父评论 ID
     */
    Integer increaseReplyCount(@Param("commentId") Long commentId);

}
