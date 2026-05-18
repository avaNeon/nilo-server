package com.neon.niloweb.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userCommentAction.CommentActionType;
import com.neon.nilocommon.entity.enums.videoComment.CommentOrderType;
import com.neon.nilocommon.entity.enums.videoComment.CommentTopType;
import com.neon.nilocommon.entity.enums.videoComment.DeleteType;
import com.neon.nilocommon.entity.enums.videoInfo.InteractionType;
import com.neon.nilocommon.entity.po.UserCommentAction;
import com.neon.nilocommon.entity.po.VideoComment;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.PageCalculator;
import com.neon.nilocommon.entity.query.VideoCommentQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.comment.VideoCommentVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.StringUtil;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.VideoCommentMapper;
import com.neon.niloweb.mapper.VideoInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class VideoCommentService
{
    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoCommentMapper <VideoComment, VideoCommentQuery> videoCommentMapper;

    private final Snowflake snowflake;

    private final WebConfig webConfig;

    /**
     * 发布视频评论
     *
     * @param userId          用户ID
     * @param videoId         视频ID
     * @param content         内容
     * @param imgPaths        图片路径
     * @param parentCommentId 父级评论ID，如果自己就是顶级评论，则为0
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public Long postComment(long userId, long videoId, String content, String imgPaths, long parentCommentId)
    {
        VideoInfo videoInfo = getVideoInfo(videoId);

        // 校验是否允许评论
        String interaction = videoInfo.getInteraction();
        if (interaction != null && (interaction.equals(InteractionType.NO_COMMENT.getValue()) || interaction.equals(
                InteractionType.NO_DANMAKU_AND_COMMENT.getValue())))
        {
            throw new BusinessException("不能评论");
        }

        VideoComment videoComment = new VideoComment();

        // 校验父级评论是否合法
        if (parentCommentId != 0)
        {
            VideoComment parentComment = getVideoComment(parentCommentId, videoId);
            if (parentComment.getDeleted() != DeleteType.UNDELETED.getValue())
            {
                throw new BusinessException("禁止发布评论");
            }
            videoComment.setReplyUserId(parentComment.getUserId());
        }

        videoComment.setVideoId(videoId);
        videoComment.setVideoUserId(videoInfo.getUserId());
        if (content != null)
        {
            videoComment.setContent(content);
        }
        if (imgPaths != null)
        {
            videoComment.setImgPaths(imgPaths);
        }
        videoComment.setParentCommentId(parentCommentId);
        videoComment.setUserId(userId);
        videoComment.setPostTime(LocalDateTime.now());
        Long commentId = snowflake.nextId();
        videoComment.setCommentId(commentId);

        // 校验完成，插入数据
        // video_comment
        videoCommentMapper.insert(videoComment);
        // 如果是回复某条评论，父评论的直接子评论计数 +1
        if (parentCommentId != 0)
        {
            videoCommentMapper.increaseReplyCount(parentCommentId);
        }
        // video_info
        videoInfoMapper.increaseByField(videoId, "comment_count", 1);

        // 最后把图片移动到COVER中
        if (imgPaths != null)
        {
            for (String imgPathStr : imgPaths.split(","))
            {
                FileUtil.verifyAndMoveCover(webConfig.getRootFilePath(), imgPathStr);
                String suffix = StringUtil.getSuffix(imgPathStr);
                String thumbnailImgPathStr = imgPathStr.substring(0,
                                                                  imgPathStr.lastIndexOf('.')) + RedisKey.THUMBNAIL_SUFFIX + suffix;
                FileUtil.verifyAndMoveCover(webConfig.getRootFilePath(), thumbnailImgPathStr);
            }
        }
        // todo 以后可以在这里通知被回复者

        return commentId;
    }

    /**
     * 获取评论列表<hr/>
     * 获取3层评论，分页大小为10条记录
     *
     * @param userId          当前登录用户ID（null 表示未登录）
     * @param videoId         视频ID
     * @param parentCommentId 父级评论ID
     * @param pageNo          页号（从1开始）
     * @param orderType       排序类型
     * @return 评论列表（已按照层级排列好）
     */
    public List <VideoCommentVO> getCommentList(Long userId,
                                                long videoId,
                                                long parentCommentId,
                                                int pageNo,
                                                String orderType,
                                                int depth)
    {
        // 校验视频是否存在
        getVideoInfo(videoId);
        if (parentCommentId != 0)
        {
            // 校验父评论是否存在本视频下
            getVideoComment(parentCommentId, videoId);
        }

        List <VideoCommentVO> commentList;
        if (orderType.equals(CommentOrderType.EARLIEST.getValue()))
        {
            commentList = getVideoCommentListBatch(userId, videoId, parentCommentId, pageNo, "post_time", depth);
        }
        else if (orderType.equals(CommentOrderType.LATEST.getValue()))
        {
            commentList = getVideoCommentListBatch(userId, videoId, parentCommentId, pageNo, "post_time DESC", depth);
        }
        else if (orderType.equals(CommentOrderType.POPULAR.getValue()))
        {
            commentList = getVideoCommentListBatch(userId,
                                                   videoId,
                                                   parentCommentId,
                                                   pageNo,
                                                   "upvote_count DESC, post_time DESC",
                                                   depth);
        }
        else
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        return commentList;
    }

    /**
     * 获取第一层评论数量（parent_comment_id = 0）
     *
     * @param videoId 视频ID
     * @return 第一层评论的数量
     */
    public int getFirstLevelCommentCount(long videoId)
    {
        // 校验视频是否存在
        getVideoInfo(videoId);
        VideoCommentQuery query = new VideoCommentQuery();
        query.setVideoId(videoId);
        query.setParentCommentId(0L);
        return videoCommentMapper.selectCount(query);
    }

    /**
     * 逻辑删除一条评论<hr/>
     * 同时也会取消置顶状态
     *
     * @param userId    用户ID
     * @param commentId 评论ID
     */
    public void deleteComment(long userId, long commentId)
    {
        VideoComment videoComment = videoCommentMapper.selectByCommentId(commentId);

        // 报错的情况：
        // 1. 评论不存在
        // 2. 评论已经被逻辑删除
        if (videoComment == null || videoComment.getDeleted() != DeleteType.UNDELETED.getValue())
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        Long commentUserId = videoComment.getUserId();
        Long videoUserId = videoComment.getVideoUserId();
        if (userId == commentUserId)
        {
            videoCommentMapper.safeDeleteByCommentId(commentId, DeleteType.DELETED_BY_USER.getValue());
        }
        else if (userId == videoUserId)
        {
            videoCommentMapper.safeDeleteByCommentId(commentId, DeleteType.DELETED_BY_VIDEO_CREATER.getValue());
        }
        // 3. 该用户不是评论发布者
        // 4. 该用户不是评论所在视频的发布者
        else
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
    }

    /**
     * 置顶评论
     *
     * @param userId    用户ID
     * @param commentId 评论ID
     */
    public void topComment(long userId, long commentId)
    {
        // 查找评论是否存在，找不到就报错
        // 评论被删除也报错
        // 如果 评论不在该用户的视频下 就报错
        // 不能给评论树中的子评论置顶
        VideoComment dbComment = videoCommentMapper.selectByCommentId(commentId);
        if (dbComment == null || dbComment.getDeleted() != DeleteType.UNDELETED.getValue() || dbComment.getVideoUserId() != userId || dbComment.getParentCommentId() != 0)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        // 如果已经置顶就不必操作
        if (dbComment.getTopType() != CommentTopType.TOP.getValue())
        {
            VideoComment updatedComment = new VideoComment();
            updatedComment.setTopType(CommentTopType.TOP.getValue());
            videoCommentMapper.updateByCommentId(updatedComment, commentId);
        }
    }

    /**
     * 取消置顶评论
     *
     * @param userId    用户ID
     * @param commentId 评论ID
     */
    public void cancelTopComment(long userId, long commentId)
    {
        // 查找评论是否存在，找不到就报错
        // 评论被删除也报错
        // 如果 评论不在该用户的视频下 就报错
        // 不能给评论树中的子评论置顶，当然也不能取消置顶
        VideoComment dbComment = videoCommentMapper.selectByCommentId(commentId);
        if (dbComment == null || dbComment.getDeleted() != DeleteType.UNDELETED.getValue() || dbComment.getVideoUserId() != userId || dbComment.getParentCommentId() != 0)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        // 如果已经是非置顶状态就不必操作
        if (dbComment.getTopType() != CommentTopType.NOT_TOP.getValue())
        {
            VideoComment updatedComment = new VideoComment();
            updatedComment.setTopType(CommentTopType.NOT_TOP.getValue());
            videoCommentMapper.updateByCommentId(updatedComment, commentId);
        }
    }

    /**
     * 根据videoId获取VideoInfo<hr/>
     * 自动校验视频是否存在，不存在会抛出异常
     *
     * @param videoId 视频ID
     * @return VideoInfo
     */
    private VideoInfo getVideoInfo(long videoId)
    {
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        // 校验视频是否存在
        if (videoInfo == null)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        return videoInfo;
    }

    /**
     * 根据commentId和本视频的videoId获取VideoComment<hr/>
     * 自动校验commentId是否合法
     *
     * @param commentId 评论ID
     * @param videoId   视频ID
     * @return 如果commentId确实代表本视频下的一条评论，则返回VideoComment，否则会抛出异常
     */
    private VideoComment getVideoComment(long commentId, long videoId)
    {
        VideoComment videoComment = videoCommentMapper.selectByCommentId(commentId);
        // 校验评论是否存在 且 是否在本视频下
        if (videoComment == null || videoComment.getVideoId() != videoId)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        return videoComment;
    }

    /**
     * 分层批量查询评论（批量查询）<hr/>
     * <b>旧方案（递归，每节点单独查）：</b> 最多 2 × (1 + N + N² + ... + N^depth) 条 SQL<br/>
     * 以 depth=3, pageSize=10 为例：最多 <b>2222条</b> SQL<br/>
     * <b>新方案（分层批量 IN + JOIN）：</b> 每层只需 1 条 IN 查询（含 JOIN），第一层视情况最多 3 条<br/>
     * <p>
     * <b>置顶分页规则（仅 parentCommentId=0 的顶层场景适用）：</b><br/>
     * 置顶评论（top_type=1）在整个评论流中始终排在最前面，普通评论（top_type=0）紧随其后，
     * 共同参与分页。设 pinnedCount 为置顶总条数，pageSize 为页大小：
     * <ul>
     *   <li>第 K 页置顶条数：{@code max(0, min(pageSize, pinnedCount - (K-1)*pageSize))}</li>
     *   <li>第 K 页普通条数：{@code pageSize - 置顶条数}</li>
     *   <li>第 K 页普通偏移：{@code max(0, (K-1)*pageSize - pinnedCount)}</li>
     * </ul>
     * 由此使置顶查询和普通查询各走独立索引，ORDER BY 中彻底去掉低区分度的 top_type 列。<br/>
     * 非顶层场景（parentCommentId≠0）不存在置顶，直接标准分页。
     * </p>
     * 每条 SQL 内部通过 JOIN user_info 和可选 LEFT JOIN user_comment_action（索引覆盖）
     * 一次性取得评论者信息与当前用户操作记录，彻底消除应用层 N+1 查询。
     *
     * @param userId          当前登录用户 ID（null 表示未登录）
     * @param videoId         视频 ID
     * @param parentCommentId 起始父评论 ID（0 = 顶层；非 0 = 某条评论的子树）
     * @param pageNo          页号（仅对第一层有效）
     * @param orderCommand    第一层普通评论的排序 SQL 片段（不含 top_type）
     * @param depth           查询层数（包括第一层）
     * @return 已组装好层级结构的第一层 VO 列表
     */
    private List <VideoCommentVO> getVideoCommentListBatch(Long userId,
                                                           long videoId,
                                                           long parentCommentId,
                                                           int pageNo,
                                                           String orderCommand,
                                                           int depth)
    {
        final int pageSize = webConfig.getCommentPageSize();
        final int limit = webConfig.getChildrenCommentPageSize();
        final List <VideoCommentVO> rootComments;

        if (parentCommentId == 0)
        {
            // ========== 顶层评论：需要处理置顶/普通混合分页 ==========

            // 1. 查置顶总条数（用于计算本页各槽位）
            VideoCommentQuery pinnedCountQuery = new VideoCommentQuery();
            pinnedCountQuery.setParentCommentId(0L);
            pinnedCountQuery.setVideoId(videoId);
            pinnedCountQuery.setTopType(1);
            int pinnedCount = videoCommentMapper.selectCount(pinnedCountQuery);

            // 2. 计算本页置顶/普通各应占多少条，以及普通评论的 SQL 起始偏移
            int pinnedOffset = (pageNo - 1) * pageSize;
            int pinnedOnPage = Math.max(0, Math.min(pageSize, pinnedCount - pinnedOffset));
            int regularOnPage = pageSize - pinnedOnPage;
            int regularOffset = Math.max(0, pinnedOffset - pinnedCount);

            // 3. 取本页置顶评论（直接用 LIMIT offset, size，无需 count）
            List <VideoCommentVO> pinned = new ArrayList <>();
            if (pinnedOnPage > 0)
            {
                VideoCommentQuery pq = new VideoCommentQuery();
                pq.setParentCommentId(0L);
                pq.setVideoId(videoId);
                pq.setTopType(1);
                pq.setOrderBy("post_time DESC"); // 置顶评论顺序从新到旧
                pq.setPageCalculator(new PageCalculator(pinnedOffset, pinnedOnPage));
                List <VideoCommentVO> result = videoCommentMapper.selectListVO(pq, userId);
                if (result != null) pinned = result;
            }

            // 4. 取本页普通评论（直接用 LIMIT offset, size，无需 count）
            List <VideoCommentVO> regular = new ArrayList <>();
            if (regularOnPage > 0)
            {
                VideoCommentQuery rq = new VideoCommentQuery();
                rq.setParentCommentId(0L);
                rq.setVideoId(videoId);
                rq.setTopType(0);
                rq.setOrderBy(orderCommand);
                rq.setPageCalculator(new PageCalculator(regularOffset, regularOnPage));
                List <VideoCommentVO> result = videoCommentMapper.selectListVO(rq, userId);
                if (result != null) regular = result;
            }

            // 5. 合并：置顶在前，普通在后
            List <VideoCommentVO> merged = new ArrayList <>(pinned);
            merged.addAll(regular);
            rootComments = merged;
        }
        else
        {
            // ========== 非顶层评论：无置顶，和子评论一样的分页大小 ==========
            VideoCommentQuery query = new VideoCommentQuery();
            query.setParentCommentId(parentCommentId);
            query.setVideoId(videoId);
            query.setOrderBy(orderCommand);
            Integer count = videoCommentMapper.selectCount(query);
            query.setPageCalculator(new PageCalculator(pageNo, count, limit));
            List <VideoCommentVO> result = videoCommentMapper.selectListVO(query, userId);
            rootComments = (result != null) ? result : new ArrayList <>();
        }

        if (rootComments.isEmpty())
        {
            return new ArrayList <>();
        }
        // 将deleted的评论内容置空
        else
        {
            rootComments.forEach(rootComment ->
                                 {
                                     if (rootComment.getDeleted() != DeleteType.UNDELETED.getValue())
                                     {
                                         rootComment.setContent("");
                                         rootComment.setImgPaths("");
                                     }
                                 });
        }


        // 建立 commentId → VO 映射，便于通过 parentCommentId 查找父 VO 并挂载子评论
        Map <Long, VideoCommentVO> voMap = new HashMap <>();
        for (VideoCommentVO vo : rootComments)
        {
            vo.setHasMoreChildren(vo.getReplyCount() != null && vo.getReplyCount() > limit);
            applyUserAction(vo);
            voMap.put(vo.getCommentId(), vo);
        }

        // 当前层的所有 comment_id，作为下一层 IN 查询的条件
        List <Long> currentParentIdList = new ArrayList <>(voMap.keySet());

        // ===== depth-1 次 SQL 查询：每层只需 1 条 IN 查询（含 JOIN）=====
        for (int d = 1 ; d < depth ; d++)
        {
            if (currentParentIdList.isEmpty())
            {
                break;
            }

            List <VideoCommentVO> children = videoCommentMapper.selectByParentIdList(currentParentIdList, videoId, limit, userId);
            if (children == null || children.isEmpty())
            {
                break;
            }
            else
            {
                children.forEach(childComment ->
                                 {
                                     if (childComment.getDeleted() != DeleteType.UNDELETED.getValue())
                                     {
                                         childComment.setContent("");
                                         childComment.setImgPaths("");
                                     }
                                 });
            }

            // 按父节点分组
            Map <Long, List <VideoCommentVO>> childrenByParent = new HashMap <>();
            for (VideoCommentVO child : children)
            {
                // 若已到搜索的最后一层，只要该节点还有子评论就标记为 hasMoreChildren，
                // 不管数量是否超过 limit（反正下一层不展示了）
                if (d == depth - 1)
                {
                    child.setHasMoreChildren(child.getReplyCount() != null && child.getReplyCount() > 0);
                }
                else
                {
                    child.setHasMoreChildren(child.getReplyCount() != null && child.getReplyCount() > limit);
                }
                applyUserAction(child);
                childrenByParent.computeIfAbsent(child.getParentCommentId(), k -> new ArrayList <>()).add(child);
            }

            // 挂载到父节点
            List <Long> nextParentIdList = new ArrayList <>();
            for (Map.Entry <Long, List <VideoCommentVO>> entry : childrenByParent.entrySet())
            {
                VideoCommentVO parentVO = voMap.get(entry.getKey());
                if (parentVO == null)
                {
                    continue;
                }

                List <VideoCommentVO> childList = entry.getValue();
                parentVO.setChildCommentList(childList);

                for (VideoCommentVO childVO : childList)
                {
                    voMap.put(childVO.getCommentId(), childVO);
                    nextParentIdList.add(childVO.getCommentId());
                }
            }
            currentParentIdList = nextParentIdList;
        }

        // 返回顶层 VO 列表（子评论已通过 setChildCommentList 逐层挂载好）
        return rootComments.stream().map(vc -> voMap.get(vc.getCommentId())).toList();
    }

    /**
     * 根据用户 action 设置 VO 对象的 “已点赞” 和 “已点踩” 情况<hr/>
     * 根据 VO 中携带的 {@code currentUserAction} 设置 isUpvoted / isDownvoted。<br/>
     * {@code currentUserAction} 为 null 时（未登录或对该评论无操作记录）不做任何修改。
     */
    private void applyUserAction(VideoCommentVO vo)
    {
        UserCommentAction action = vo.getCurrentUserAction();
        if (action == null || action.getActionType() == null)
        {
            return;
        }
        if (action.getActionType().equals(CommentActionType.UPVOTE.getValue()))
        {
            vo.setUpvoted(true);
        }
        else if (action.getActionType().equals(CommentActionType.DOWNVOTE.getValue()))
        {
            vo.setDownvoted(true);
        }
    }

}
