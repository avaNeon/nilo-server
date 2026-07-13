package com.neon.nilocomment.mapper;

import com.neon.nilocommon.entity.dto.comment.CommentDailyStatisticsDTO;
import com.neon.nilocommon.entity.po.VideoComment;
import com.neon.nilocommon.entity.vo.comment.CommentManagementAdmin;
import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import com.neon.nilocommon.entity.vo.comment.VideoCommentVO;
import com.neon.nilocommon.util.PageCalculator;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /**
     * 按评论者 user_id 批量更新冗余昵称
     */
    Integer updateNickNameByUserId(@Param("userId") Long userId, @Param("nickName") String nickName);

    /**
     * 按被回复者 reply_user_id 批量更新冗余回复昵称
     */
    Integer updateReplyNickNameByReplyUserId(@Param("replyUserId") Long replyUserId,
                                             @Param("replyNickName") String replyNickName);

    /**
     * 按评论者 user_id 批量更新冗余头像
     */
    Integer updateAvatarByUserId(@Param("userId") Long userId, @Param("avatar") String avatar);

    /**
     * 根据评论ID列表批量查询评论信息
     */
    List <VideoComment> selectBatchByCommentIdList(@Param("commentIdList") List <Long> commentIdList);

    /**
     * 查询指定父评论下一级的评论ID列表
     */
    List <Long> selectChildCommentIdList(@Param("parentCommentIdList") List <Long> parentCommentIdList);

    /**
     * 查询发布时间范围内已逻辑删除的评论ID列表
     */
    List <Long> selectDeletedCommentIdListByPostTimeRange(@Param("postTimeStart") LocalDate postTimeStart,
                                                          @Param("postTimeEnd") LocalDate postTimeEnd);

    /**
     * 查询指定评论ID列表中已逻辑删除的评论数量
     */
    Integer selectDeletedCountByCommentIdList(@Param("commentIdList") List <Long> commentIdList);

    /**
     * 根据CommentId列表真正删除评论（不限制逻辑删除标志）
     */
    Integer destroyByCommentIdList(@Param("commentIdList") List <Long> commentIdList);

    /**
     * 根据CommentId列表真正删除已逻辑删除评论
     */
    Integer destroyDeletedByCommentIdList(@Param("commentIdList") List <Long> commentIdList);

    /**
     * 统一减少评论的回复数量
     */
    Integer decreaseBatchReplyCount(@Param("commentIds") List <Long> commentIds, @Param("count") Integer count);

    /**
     * 评论管理页面（admin），查询数量（含已删除）
     */
    Long selectCommentManagementCount(@Param("nameFuzzy") String nameFuzzy);

    /**
     * 评论管理页面（admin），查询列表（含已删除）
     */
    List <CommentManagementAdmin> selectCommentManagement(@Param("nameFuzzy") String nameFuzzy,
                                                          @Param("start") Integer start,
                                                          @Param("pageSize") Integer pageSize);

    /**
     * 按 video_id 批量更新冗余视频标题
     */
    Integer updateVideoNameByVideoId(@Param("videoId") Long videoId, @Param("videoName") String videoName);

    /**
     * 按 video_id 批量更新冗余视频封面
     */
    Integer updateVideoCoverByVideoId(@Param("videoId") Long videoId, @Param("videoCover") String videoCover);

    /**
     * 按视频作者聚合指定时间范围内收到的评论数（仅未删除）
     *
     * @param startDate 开始时间，闭区间
     * @param endDate   结束时间，开区间
     */
    List <CommentDailyStatisticsDTO> selectDailyCommentCountByVideoUserId(@Param("startDate") LocalDateTime startDate,
                                                                          @Param("endDate") LocalDateTime endDate);
}
