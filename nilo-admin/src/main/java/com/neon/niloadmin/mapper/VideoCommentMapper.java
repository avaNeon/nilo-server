package com.neon.niloadmin.mapper;

import com.neon.nilocommon.entity.po.VideoComment;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 评论 数据库操作接口
 */
public interface VideoCommentMapper<T, P> extends BaseMapper <T, P>
{
    VideoComment selectByCommentId(@Param("commentId") Long commentId);

    /**
     * 查询指定父评论下一级的评论ID列表。
     */
    List <Long> selectChildCommentIdList(@Param("parentCommentIdList") List <Long> parentCommentIdList);

    /**
     * 查询发布时间范围内已逻辑删除的评论ID列表。
     */
    List <Long> selectDeletedCommentIdListByPostTimeRange(@Param("postTimeStart") LocalDate postTimeStart,
                                                          @Param("postTimeEnd") LocalDate postTimeEnd);

    /**
     * 查询指定评论ID列表中已逻辑删除的评论数量。
     */
    Integer selectDeletedCountByCommentIdList(@Param("commentIdList") List <Long> commentIdList);

    /**
     * 根据CommentId列表真正删除评论（不限制逻辑删除标志）。
     */
    Integer destroyByCommentIdList(@Param("commentIdList") List <Long> commentIdList);

    /**
     * 根据CommentId列表真正删除已逻辑删除评论。
     */
    Integer destroyDeletedByCommentIdList(@Param("commentIdList") List <Long> commentIdList);

    /**
     * 根据CommentId逻辑删除（将 deleted 更新为 1）
     */
    Integer safeDeleteByCommentId(@Param("commentId") Long commentId, @Param("deletedType") Integer deletedType);

}
