package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import com.neon.nilocommon.entity.vo.comment.VideoCommentVO;
import com.neon.nilocommon.util.PageCalculator;
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
     * 根据CommentId真正删除（仅允许删除已逻辑删除的评论）
     */
    Integer destroyByCommentId(@Param("commentId") Long commentId);

    /**
     * 根据CommentId逻辑删除（将 deleted 更新为 1）
     */
    Integer safeDeleteByCommentId(@Param("commentId") Long commentId, @Param("deletedType") Integer deletedType);

    /**
     * 根据CommentId获取对象
     */
    T selectByCommentId(@Param("commentId") Long commentId);

    /**
     * <p>
     *     批量查询父评论的子评论（per-parent top-K），同时 JOIN user_info 获取评论者昵称/头像，<br/>
     *     并可选 LEFT JOIN user_comment_action 获取当前用户对每条评论的操作记录（索引覆盖）。
     * </p>
     * <p>使用 ROW_NUMBER() OVER (PARTITION BY parent_comment_id ORDER BY post_time ASC) </p>
     * <p>保证每个父节点最多返回limitPerParent 条，结果直接映射到 {@link VideoCommentVO}。</p>
     * <p>是否还有更多子评论由调用方通过 {@code replyCount > limitPerParent} 判断，无需 K+1 探查。</p>
     *
     * @param parentIdList   父节点 comment_id 列表
     * @param videoId        视频 ID
     * @param limitPerParent 每个父节点最多返回的子评论数
     * @param userId         当前登录用户 ID（null 表示未登录，不查询操作记录）
     * @return 子评论 VO 列表，每父最多 limitPerParent 条
     */
    List <VideoCommentVO> selectByParentIdList(@Param("parentIdList") List <Long> parentIdList,
                                               @Param("videoId") long videoId,
                                               @Param("limitPerParent") int limitPerParent,
                                               @Param("userId") Long userId);

    /**
     * 与{@link VideoCommentMapper} 的 selectListVO 方法基本一致，但是同时查询置顶评论和非置顶评论<hr/>
     * 默认顶层评论排在前面
     *
     * @param videoId        视频ID
     * @param orderBy        排序顺序（postTime正序/倒序、upvote_post倒序性能好）
     * @param pageCalculator 分页器
     * @param userId         用户ID（可选）
     * @return 视频列表
     */
    List <VideoCommentVO> selectListAllTopKindsVO(@Param("parentCommentId") Long parentCommentId,
                                                  @Param("videoId") Long videoId,
                                                  @Param("orderBy") String orderBy,
                                                  @Param("pageCalculator") PageCalculator pageCalculator,
                                                  @Param("userId") Long userId);

    /**
     * 分页查询评论列表 VO，等价于 selectList 但额外 JOIN user_info 获取评论者信息，
     * 并可选 LEFT JOIN user_comment_action 获取当前用户操作记录，结果直接映射到 {@link VideoCommentVO}。
     *
     * @param query  查询条件（含分页、排序等）
     * @param userId 当前登录用户 ID（null 表示未登录，不查询操作记录）
     * @return 评论 VO 列表（已分页）
     */
    List <VideoCommentVO> selectListVO(@Param("query") P query, @Param("userId") Long userId);

    /**
     * 将指定评论的直接子评论计数 reply_count +1。<br/>
     * 在 postComment 发布回复时调用（parentCommentId != 0 的情况）。
     *
     * @param commentId 被回复的父评论 ID
     */
    Integer increaseReplyCount(@Param("commentId") Long commentId);

    Integer increaseUpvoteCount(@Param("commentId") Long commentId);

    Integer decreaseUpVoteCount(@Param("commentId") Long commentId);

    Integer increaseDownvoteCount(@Param("commentId") Long commentId);

    Integer decreaseDownvoteCount(@Param("commentId") Long commentId);

    /**
     * 汇总指定用户评论获得的点赞数
     *
     * @param userId 用户ID
     * @return 评论点赞总数
     */
    Long selectUpvoteCountByUserId(@Param("userId") Long userId);

    /**
     * 评论管理页面，查询多个视频的评论信息数量
     */
    Long selectCommentManagementVOCount(@Param("userId") Long userId,
                                        @Param("videoId") Long videoId,
                                        @Param("nameFuzzy") String nameFuzzy);

    /**
     * 评论管理页面，查询多个视频的评论信息
     */
    List <CommentManagementVO> selectCommentManagementVO(@Param("userId") Long userId,
                                                         @Param("videoId") Long videoId,
                                                         @Param("nameFuzzy") String nameFuzzy,
                                                         @Param("start") Integer start,
                                                         @Param("pageSize") Integer pageSize);
}
