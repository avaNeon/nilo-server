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
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.po.VideoComment;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.query.VideoCommentQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.comment.VideoCommentVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.nilocommon.util.StringUtil;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.UserInfoMapper;
import com.neon.niloweb.mapper.VideoCommentMapper;
import com.neon.niloweb.mapper.VideoInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoCommentService
{
    /* Service */

    private final UserMessageService userMessageService;

    /* Repository */

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoCommentMapper <VideoComment, VideoCommentQuery> videoCommentMapper;

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    /* Other */

    private final Snowflake snowflake;

    private final WebConfig webConfig;
    /**
     * 最大置顶评论条数
     */
    private static final int MOST_TOP_COMMENT_COUNT = 5;

    /**
     * 发布视频评论
     *
     * @param userId          用户ID
     * @param videoId         视频ID
     * @param content         内容
     * @param imgPaths        图片路径
     * @param parentCommentId 父级评论ID，如果自己就是顶级评论，则为0
     * @return 评论ID
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

        VideoComment parentComment = null;

        // 校验父级评论是否合法
        if (parentCommentId != 0)
        {
            parentComment = getVideoComment(parentCommentId, videoId);
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

        /* 校验完成，插入数据 */
        // video_comment
        videoCommentMapper.insert(videoComment);

        // 如果是回复某条评论，父评论的直接子评论计数 +1
        if (parentCommentId != 0)
        {
            // 父评论子评论数+1
            videoCommentMapper.increaseReplyCount(parentCommentId);
        }

        // 视频的评论数+1
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

        if (parentCommentId != 0)
        {
            String replyCommentContent = formatReplyCommentContent(parentComment);

            // 异步向父评论发布用户发送通知
            CompletableFuture <Void> replyCommentMessage = userMessageService.recordCommentMessage(videoComment.getReplyUserId(),
                                                                                                   userId,
                                                                                                   videoId,
                                                                                                   content,
                                                                                                   replyCommentContent);

            CompletableFuture.allOf(replyCommentMessage).exceptionally(e ->
                                                                       {
                                                                           log.warn("异步发送给用户评论被回复消息时产生异常：{}",
                                                                                    e.toString());
                                                                           return null;
                                                                       });
        }

        // 如果回复者就是视频发布者，没必要再给发布者发消息了
        if (!Objects.equals(videoComment.getReplyUserId(), videoInfo.getUserId()))
        {
            // 异步向视频发布者发送新评论通知
            CompletableFuture <Void> videoCommentMessage = userMessageService.recordCommentMessage(videoInfo.getUserId(),
                                                                                                   userId,
                                                                                                   videoId,
                                                                                                   content,
                                                                                                   null);

            CompletableFuture.allOf(videoCommentMessage).exceptionally(e ->
                                                                       {
                                                                           log.warn("异步发送视频出现新评论消息时产生异常：{}",
                                                                                    e.toString());
                                                                           return null;
                                                                       });
        }

        return commentId;
    }

    /**
     * 获取评论列表<hr/>
     * 获取3层评论，分页大小为6条记录
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
            commentList = getVideoCommentListBatch(userId, videoId, parentCommentId, pageNo, "v.post_time", depth);
        }
        else if (orderType.equals(CommentOrderType.LATEST.getValue()))
        {
            commentList = getVideoCommentListBatch(userId, videoId, parentCommentId, pageNo, "v.post_time DESC", depth);
        }
        else if (orderType.equals(CommentOrderType.POPULAR.getValue()))
        {
            commentList = getVideoCommentListBatch(userId,
                                                   videoId,
                                                   parentCommentId,
                                                   pageNo,
                                                   "v.upvote_count DESC, v.post_time DESC",
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
    @Transactional
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
        Integer deletedCount;
        if (userId == commentUserId)
        {
            deletedCount = videoCommentMapper.safeDeleteByCommentId(commentId, DeleteType.DELETED_BY_USER.getValue());
        }
        else if (userId == videoUserId)
        {
            deletedCount = videoCommentMapper.safeDeleteByCommentId(commentId, DeleteType.DELETED_BY_VIDEO_CREATER.getValue());
        }
        // 3. 该用户不是评论发布者
        // 4. 该用户不是评论所在视频的发布者
        else
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }

        if (deletedCount != null && deletedCount > 0)
        {
            videoInfoMapper.decreaseByField(videoComment.getVideoId(), "comment_count", 1);
        }
        else
        {
            throw new BusinessException("删除失败");
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

        // 限制视频指定条数，不能置顶超过5条评论
        VideoCommentQuery query = new VideoCommentQuery();
        query.setVideoId(dbComment.getVideoId());
        query.setParentCommentId(0L);
        query.setTopType(CommentTopType.TOP.getValue());
        Integer count = videoCommentMapper.selectCount(query);
        if (count >= MOST_TOP_COMMENT_COUNT)
        {
            throw new BusinessException("置顶评论条数达到上限");
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

    private String formatReplyCommentContent(VideoComment parentComment)
    {
        UserInfo replyUser = userInfoMapper.selectByUserId(parentComment.getUserId());

        String nickName = replyUser.getNickName();
        // 如果找不到昵称（不太可能），兜底返回UID
        if (replyUser.getNickName() == null || replyUser.getNickName().isBlank())
        {
            nickName = String.valueOf(parentComment.getUserId());
        }

        String content = parentComment.getContent();
        // 如果是纯图片回复，这里就显示“[图片]”
        if ((content == null || content.isBlank()) && parentComment.getImgPaths() != null && !parentComment.getImgPaths()
                                                                                                           .isBlank())
        {
            content = "[图片]";
        }

        return nickName + "：" + (content == null ? "" : content);
    }

    /**
     * <b>分层批量查询评论（批量查询）</b><hr/>
     * <p>
     * <b>旧方案（递归，每节点单独查）：<br/>
     * </b> 最多 1 + N + N² + ... + N^layer 条 SQL<br/>
     * 以 layer=4, pageSize=10 为例：最多 <b>1111条</b> SQL<br/>
     * </p>
     * <p>
     * <b>新方案（分层批量 IN + JOIN）：<br/>
     * </b> 每层只需 1 条 IN 查询（含 JOIN），第一层视情况最多 3 条<br/>
     * </p>
     * <p>
     * <em>置顶评论的特殊对待已经删除，因为现在限制最多置顶5条评论</em>
     * </p>
     * <p>
     * 每条 SQL 内部通过 JOIN user_info 和可选 LEFT JOIN user_comment_action（索引覆盖）
     * 一次性取得评论者信息与当前用户操作记录，彻底消除应用层 N+1 查询。
     * </p>
     *
     * @param userId          当前登录用户 ID（null 表示未登录）
     * @param videoId         视频 ID
     * @param parentCommentId 起始父评论 ID（0 = 顶层；非 0 = 某条评论的子树）
     * @param pageNo          页号（仅对第一层有效）
     * @param orderCommand    第一层普通评论的排序 SQL 片段（不含 top_type）
     * @param layer           查询层数（包括第一层）
     * @return 已组装好层级结构的第一层 VO 列表
     */
    private List <VideoCommentVO> getVideoCommentListBatch(Long userId,
                                                           long videoId,
                                                           long parentCommentId,
                                                           int pageNo,
                                                           String orderCommand,
                                                           int layer)
    {

        final int childPageSize = webConfig.getChildrenCommentPageSize();

        // 查询评论
        final int pageSize;
        final int pageIndex;
        List <VideoCommentVO> rootCommentList;

        // 如果是顶级评论
        if (parentCommentId == 0)
        {
            pageSize = webConfig.getCommentPageSize();
        }
        // 如果是子评论（子评论的有自己的页大小）
        else
        {
            pageSize = webConfig.getChildrenCommentPageSize();
        }

        pageIndex = (pageNo - 1) * pageSize;

        rootCommentList = videoCommentMapper.selectListAllTopKindsVO(parentCommentId,
                                                                     videoId,
                                                                     orderCommand,
                                                                     new PageCalculator(pageIndex, pageSize),
                                                                     userId);

        // 如果前面的查询结果都是空，那么返回空列表
        if (rootCommentList == null || rootCommentList.isEmpty())
        {
            return new ArrayList <>();
        }

        // 否则，将deleted的评论内容置空
        else
        {
            rootCommentList.forEach(rootComment ->
                                    {
                                        if (rootComment.getDeleted() != DeleteType.UNDELETED.getValue())
                                        {
                                            rootComment.setContent("");
                                            rootComment.setImgPaths("");
                                        }
                                    });
        }

        // 建立 commentId → VO 映射，便于通过 parentCommentId 查找父 VO 并挂载子评论
        Map <Long, VideoCommentVO> voMap = rootCommentList.stream().collect(Collectors.toMap(VideoCommentVO::getCommentId, vo ->
        {
            // 设置评论是否展示“显示更多评论”
            vo.setHasMoreChildren(vo.getReplyCount() != null && vo.getReplyCount() > childPageSize);

            // 根据用户操作显示“已点赞”、“已点踩”
            applyUserAction(vo);

            return vo;
        }));

        // 当前层的所有 comment_id，作为下一层 IN 查询的条件
        List <Long> currentParentIdList = new ArrayList <>(voMap.keySet());

        // ===== layer-1 次 SQL 查询：每层只需 1 条 IN 查询（含 JOIN）=====
        for (int i = 1 ; i < layer ; i++)
        {
            // 如果上一层没有评论，结束
            if (currentParentIdList.isEmpty())
            {
                break;
            }

            // 查询一层所有评论
            List <VideoCommentVO> children = videoCommentMapper.selectByParentIdList(currentParentIdList,
                                                                                     videoId,
                                                                                     childPageSize,
                                                                                     userId);

            // 如果子评论没有记录，就不用继续往下一层查找了
            if (children == null || children.isEmpty())
            {
                break;
            }
            // 否则，把子评论中的被删除的记录的评论内容删除
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

            // 为了适配 lambda 表达式所写的值
            final int thisLayer = i;

            // 按父节点分组
            Map <Long, List <VideoCommentVO>> childrenByParent = children.stream()
                                                                         .peek(child ->
                                                                               {
                                                                                   // 设置是否还有子评论标记
                                                                                   // 若已到搜索的最后一层，只要该节点还有子评论就标记为 hasMoreChildren，不管数量是否超过 childPageSize
                                                                                   if (thisLayer == layer - 1)
                                                                                   {
                                                                                       child.setHasMoreChildren(child.getReplyCount() != null && child.getReplyCount() > 0);
                                                                                   }
                                                                                   else
                                                                                   {
                                                                                       child.setHasMoreChildren(child.getReplyCount() != null && child.getReplyCount() > childPageSize);
                                                                                   }
                                                                                   applyUserAction(child);
                                                                               })
                                                                         .collect(Collectors.groupingBy(VideoCommentVO::getParentCommentId));

            // 下一轮父节点
            List <Long> nextParentIdList = new ArrayList <>();

            for (Map.Entry <Long, List <VideoCommentVO>> entry : childrenByParent.entrySet())
            {
                VideoCommentVO parentVO = voMap.get(entry.getKey());
                if (parentVO == null)
                {
                    continue;
                }

                // 将子节点挂载到父节点
                List <VideoCommentVO> childList = entry.getValue();
                parentVO.setChildCommentList(childList);

                // 将子节点加入到voMap映射中
                for (VideoCommentVO childVO : childList)
                {
                    voMap.put(childVO.getCommentId(), childVO);
                    nextParentIdList.add(childVO.getCommentId());
                }
            }

            // 更新父节点
            currentParentIdList = nextParentIdList;
        }

        // 返回顶层 VO 列表（子评论已通过 setChildCommentList 逐层挂载好）
        return rootCommentList.stream().map(vc -> voMap.get(vc.getCommentId())).toList();
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
