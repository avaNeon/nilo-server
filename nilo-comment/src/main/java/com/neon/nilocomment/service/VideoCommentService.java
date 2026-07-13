package com.neon.nilocomment.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.nilocomment.config.CommentConfig;
import com.neon.nilocomment.feign.storage.InnerImageFeignClient;
import com.neon.nilocomment.feign.web.InnerMediaOwnershipFeignClient;
import com.neon.nilocomment.feign.web.InnerUserFeignClient;
import com.neon.nilocomment.feign.web.InnerUserMessageFeignClient;
import com.neon.nilocomment.feign.web.InnerVideoFeignClient;
import com.neon.nilocomment.mapper.*;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.dto.CommentMessageDTO;
import com.neon.nilocommon.entity.dto.MediaOwnershipBatchDTO;
import com.neon.nilocommon.entity.dto.UserInfoDTO;
import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import com.neon.nilocommon.entity.dto.comment.CommentDailyStatisticsDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userCommentAction.CommentActionType;
import com.neon.nilocommon.entity.enums.userInfo.UserStatus;
import com.neon.nilocommon.entity.enums.videoComment.CommentOrderType;
import com.neon.nilocommon.entity.enums.videoComment.CommentTopType;
import com.neon.nilocommon.entity.enums.videoComment.DeleteType;
import com.neon.nilocommon.entity.enums.videoInfo.InteractionType;
import com.neon.nilocommon.entity.po.UserCommentAction;
import com.neon.nilocommon.entity.po.UserCommentActionArchive;
import com.neon.nilocommon.entity.po.VideoComment;
import com.neon.nilocommon.entity.po.VideoCommentArchive;
import com.neon.nilocommon.entity.query.UserCommentActionArchiveQuery;
import com.neon.nilocommon.entity.query.UserCommentActionQuery;
import com.neon.nilocommon.entity.query.VideoCommentArchiveQuery;
import com.neon.nilocommon.entity.query.VideoCommentQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementAdmin;
import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import com.neon.nilocommon.entity.vo.comment.VideoCommentVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.PageCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoCommentService
{
    /* Feign */

    private final InnerVideoFeignClient innerVideoFeignClient;

    private final InnerUserFeignClient innerUserFeignClient;

    private final InnerMediaOwnershipFeignClient innerMediaOwnershipFeignClient;

    private final InnerUserMessageFeignClient innerUserMessageFeignClient;

    private final InnerImageFeignClient innerImageFeignClient;

    /* Repository */

    private final VideoCommentMapper <VideoComment, VideoCommentQuery> videoCommentMapper;

    private final VideoCommentArchiveMapper <VideoCommentArchive, VideoCommentArchiveQuery> videoCommentArchiveMapper;

    private final UserCommentActionMapper <UserCommentAction, UserCommentActionQuery> userCommentActionMapper;

    private final UserCommentActionArchiveMapper <UserCommentActionArchive, UserCommentActionArchiveQuery> userCommentActionArchiveMapper;

    /* Other */

    private final Snowflake snowflake;

    private final CommentConfig commentConfig;

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
     * @param imgKeys         图片keys
     * @param parentCommentId 父级评论ID，如果自己就是顶级评论，则为0
     * @return 评论ID
     */
    @GlobalTransactional(rollbackFor = Exception.class)
    public Long postComment(long userId, long videoId, String content, String imgKeys, long parentCommentId)
    {
        VideoSnapshotDTO videoInfo = getVideoInfo(videoId);

        // 校验是否允许评论
        String interaction = videoInfo.getInteraction();
        if (interaction != null && (interaction.equals(InteractionType.NO_COMMENT.getValue()) || interaction.equals(
                InteractionType.NO_DANMAKU_AND_COMMENT.getValue())))
        {
            throw new BusinessException("不能评论");
        }

        VideoComment videoComment = new VideoComment();

        VideoComment parentComment = null;

        // 检查用户是否存在且可用
        UserInfoDTO userInfo = getUserInfo(userId);
        if (userInfo == null || Objects.equals(userInfo.getStatus(), UserStatus.DISABLE.status))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 校验父级评论是否合法
        if (parentCommentId != 0)
        {
            parentComment = getVideoComment(parentCommentId, videoId);
            if (parentComment.getDeleted() != DeleteType.UNDELETED.getValue())
            {
                throw new BusinessException("禁止发布评论");
            }
            videoComment.setReplyUserId(parentComment.getUserId());
            videoComment.setReplyNickName(parentComment.getNickName());
        }

        videoComment.setVideoId(videoId);
        videoComment.setVideoName(videoInfo.getVideoName());
        videoComment.setVideoCover(videoInfo.getVideoCover());
        videoComment.setVideoUserId(videoInfo.getUserId());
        videoComment.setNickName(userInfo.getNickName());
        videoComment.setAvatar(userInfo.getAvatar());
        if (content != null)
        {
            videoComment.setContent(content);
        }
        if (imgKeys != null)
        {
            videoComment.setImgPaths(imgKeys);
        }
        videoComment.setParentCommentId(parentCommentId);
        videoComment.setUserId(userId);
        videoComment.setPostTime(LocalDateTime.now());
        Long commentId = snowflake.nextId();
        videoComment.setCommentId(commentId);

        // 校验图片归属权
        if (imgKeys != null)
        {
            String[] imgKeyArray = imgKeys.split(",");
            List <String> allKeys = new ArrayList <>();
            for (String imgKey : imgKeyArray)
            {
                allKeys.add(imgKey);
                allKeys.add(FileUtil.constructThumbnailName(imgKey));
            }

            MediaOwnershipBatchDTO validateRequest = new MediaOwnershipBatchDTO();
            validateRequest.setObjectKeys(allKeys);
            validateRequest.setOwnerId(userId);
            validateRequest.setUsed(0);
            ResponseVO <Integer> validateResult = innerMediaOwnershipFeignClient.validate(validateRequest);
            if (!ResponseCode.SUCCESS.getCode()
                                     .equals(validateResult.getCode()) || validateResult.getData() == null || validateResult.getData() != allKeys.size())
            {
                throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
            }
        }

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
        ResponseVO <Void> increaseResult = innerVideoFeignClient.increaseCommentCount(videoId, 1);
        if (!ResponseCode.SUCCESS.getCode().equals(increaseResult.getCode()))
        {
            throw new BusinessException("更新视频评论数失败");
        }

        // 标记图片资源为已使用并移动到public
        if (imgKeys != null)
        {
            LocalDateTime curDate = LocalDateTime.now();

            // 收集所有需要标记的key（图片+缩略图）
            String[] imgKeyArray = imgKeys.split(",");
            List <String> allKeys = new ArrayList <>();
            for (String imgKey : imgKeyArray)
            {
                allKeys.add(imgKey);
                allKeys.add(FileUtil.constructThumbnailName(imgKey));
            }

            // 批量标记为已使用
            MediaOwnershipBatchDTO markRequest = new MediaOwnershipBatchDTO();
            markRequest.setObjectKeys(allKeys);
            markRequest.setOwnerId(userId);
            markRequest.setUsedTime(curDate);
            ResponseVO <Integer> markResult = innerMediaOwnershipFeignClient.markAsUsed(markRequest);
            if (!ResponseCode.SUCCESS.getCode()
                                     .equals(markResult.getCode()) || markResult.getData() == null || markResult.getData() != allKeys.size())
            {
                throw new BusinessException("部分图片资源不存在或已被使用");
            }

            // 再移动图片和缩略图到public
            for (String imgKey : imgKeyArray)
            {
                String thumbnailKey = FileUtil.constructThumbnailName(imgKey);
                ResponseVO <Void> imgResult = innerImageFeignClient.move(MinioKey.TMP_PREFIX + imgKey,
                                                                         MinioKey.PUBLIC_PREFIX + imgKey);
                ResponseVO <Void> thumbResult = innerImageFeignClient.move(MinioKey.TMP_PREFIX + thumbnailKey,
                                                                           MinioKey.PUBLIC_PREFIX + thumbnailKey);
                if (!imgResult.getCode().equals(ResponseCode.SUCCESS.getCode()) || !thumbResult.getCode()
                                                                                               .equals(ResponseCode.SUCCESS.getCode()))
                {
                    throw new BusinessException("图片移动失败");
                }
            }
        }

        // 给回复人发消息
        // 如果父评论存在并且回复的不是自己的评论，就给回复者发通知
        if (parentCommentId != 0 && !Objects.equals(parentComment.getUserId(), userId))
        {
            String replyCommentContent = formatReplyCommentContent(parentComment);
            String postedCommentContent = formatPostedCommentContent(content, imgKeys);
            sendCommentMessageSafely(videoComment.getReplyUserId(),
                                     userId,
                                     videoId,
                                     postedCommentContent,
                                     replyCommentContent,
                                     "异步发送给用户评论被回复消息时产生异常：{}");
        }

        // 通知视频发布者新消息
        // 如果评论者就是视频发布者，没必要再给发布者（自己）发消息了
        if (!Objects.equals(userId, videoInfo.getUserId()))
        {
            String postedCommentContent = formatPostedCommentContent(content, imgKeys);
            sendCommentMessageSafely(videoInfo.getUserId(),
                                     userId,
                                     videoId,
                                     postedCommentContent,
                                     null,
                                     "异步发送视频出现新评论消息时产生异常：{}");
        }

        return commentId;
    }

    /**
     * 获取评论列表<hr/>
     * 获取3层评论，分页大小根据配置设置
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

        if (deletedCount == null || deletedCount == 0)
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
     * Admin：评论管理列表数量（含已删除）
     */
    public Long getAdminCommentManagementInfoCount(String nameFuzzy)
    {
        if (nameFuzzy == null || nameFuzzy.isBlank())
        {
            nameFuzzy = null;
        }
        return videoCommentMapper.selectCommentManagementCount(nameFuzzy);
    }

    /**
     * Admin：评论管理列表（含已删除）
     */
    public List <CommentManagementAdmin> getAdminCommentManagementInfo(String nameFuzzy, int pageNo, int pageSize)
    {
        if (nameFuzzy == null || nameFuzzy.isBlank())
        {
            nameFuzzy = null;
        }
        int start = (pageNo - 1) * pageSize;
        return videoCommentMapper.selectCommentManagement(nameFuzzy, start, pageSize);
    }

    /**
     * 创作中心：评论管理数量
     */
    public Long getCreatorCommentManagementInfoCount(long userId, Long videoId, String nameFuzzy)
    {
        if (nameFuzzy == null || nameFuzzy.isBlank())
        {
            nameFuzzy = null;
        }
        return videoCommentMapper.selectCommentManagementVOCount(userId, videoId, nameFuzzy);
    }

    /**
     * 创作中心：评论管理列表
     */
    public List <CommentManagementVO> getCreatorCommentManagementInfo(long userId,
                                                                      Long videoId,
                                                                      String nameFuzzy,
                                                                      int pageNo,
                                                                      int pageSize)
    {
        if (nameFuzzy == null || nameFuzzy.isBlank())
        {
            nameFuzzy = null;
        }
        int start = (pageNo - 1) * pageSize;
        return videoCommentMapper.selectCommentManagementVO(userId, videoId, nameFuzzy, start, pageSize);
    }

    /**
     * Admin：逻辑删除指定评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCommentByAdmin(long commentId)
    {
        VideoComment videoComment = videoCommentMapper.selectByCommentId(commentId);
        if (videoComment == null)
        {
            throw new BusinessException("不存在此条记录");
        }
        if (videoComment.getDeleted() == null || videoComment.getDeleted() != DeleteType.UNDELETED.getValue())
        {
            throw new BusinessException("评论已被删除");
        }

        Integer deletedCount = videoCommentMapper.safeDeleteByCommentId(commentId, DeleteType.DELETED_BY_ADMIN.getValue());
        if (deletedCount == null || deletedCount == 0)
        {
            throw new BusinessException("评论未被删除");
        }
    }

    /**
     * Admin：真正删除指定已逻辑删除的评论
     */
    @GlobalTransactional(rollbackFor = Exception.class)
    public void destroyComment(long commentId)
    {
        destroyCommentTree(List.of(commentId));
    }

    /**
     * Admin：真正删除发布时间在指定日期范围内且已逻辑删除的评论
     */
    @GlobalTransactional(rollbackFor = Exception.class)
    public void destroyCommentsByPostTimeRange(LocalDate postTimeStart, LocalDate postTimeEnd)
    {
        if (postTimeStart == null || postTimeEnd == null)
        {
            throw new BusinessException("postTimeStart和postTimeEnd不能为空");
        }
        if (postTimeStart.isAfter(postTimeEnd))
        {
            throw new BusinessException("postTimeStart不能晚于postTimeEnd");
        }

        List <Long> commentIdList = videoCommentMapper.selectDeletedCommentIdListByPostTimeRange(postTimeStart, postTimeEnd);
        destroyCommentTree(commentIdList);
    }

    /**
     * 将指定视频下的评论及评论行为归档（视频删除时调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void archiveByVideoId(long videoId)
    {
        VideoCommentQuery videoCommentQuery = new VideoCommentQuery();
        videoCommentQuery.setVideoId(videoId);
        List <VideoComment> videoCommentList = videoCommentMapper.selectList(videoCommentQuery);

        VideoCommentArchiveQuery videoCommentArchiveQuery = new VideoCommentArchiveQuery();
        videoCommentArchiveQuery.setVideoId(videoId);
        videoCommentArchiveMapper.deleteByParam(videoCommentArchiveQuery);
        copyBatch(videoCommentList, VideoCommentArchive::new, videoCommentArchiveMapper);

        UserCommentActionQuery userCommentActionQuery = new UserCommentActionQuery();
        userCommentActionQuery.setVideoId(videoId);
        List <UserCommentAction> userCommentActionList = userCommentActionMapper.selectList(userCommentActionQuery);

        UserCommentActionArchiveQuery userCommentActionArchiveQuery = new UserCommentActionArchiveQuery();
        userCommentActionArchiveQuery.setVideoId(videoId);
        userCommentActionArchiveMapper.deleteByParam(userCommentActionArchiveQuery);
        copyBatch(userCommentActionList, UserCommentActionArchive::new, userCommentActionArchiveMapper);

        userCommentActionMapper.deleteByParam(userCommentActionQuery);
        videoCommentMapper.deleteByParam(videoCommentQuery);
    }

    /**
     * 从归档恢复指定视频下的评论及评论行为<hr/>
     * <p>幂等：若归档数据已不存在（说明本次恢复此前已经执行成功），直接视为成功返回，
     * 不再校验目标表是否为空，避免 MQ 重复投递触发误报错误并进入死信队列</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void restoreByVideoId(long videoId)
    {
        VideoCommentArchiveQuery videoCommentArchiveQuery = new VideoCommentArchiveQuery();
        videoCommentArchiveQuery.setVideoId(videoId);
        List <VideoCommentArchive> videoCommentArchiveList = videoCommentArchiveMapper.selectList(videoCommentArchiveQuery);

        UserCommentActionArchiveQuery userCommentActionArchiveQuery = new UserCommentActionArchiveQuery();
        userCommentActionArchiveQuery.setVideoId(videoId);
        List <UserCommentActionArchive> userCommentActionArchiveList = userCommentActionArchiveMapper.selectList(
                userCommentActionArchiveQuery);

        // 归档表已经没有数据，说明本次恢复已经完成过（或本身没有归档数据），直接视为成功，保证重复消费时的幂等性
        if ((videoCommentArchiveList == null || videoCommentArchiveList.isEmpty()) && (userCommentActionArchiveList == null || userCommentActionArchiveList.isEmpty()))
        {
            return;
        }

        VideoCommentQuery videoCommentQuery = new VideoCommentQuery();
        videoCommentQuery.setVideoId(videoId);
        assertTargetEmpty(videoCommentQuery, videoCommentMapper);
        copyBatch(videoCommentArchiveList, VideoComment::new, videoCommentMapper);

        UserCommentActionQuery userCommentActionQuery = new UserCommentActionQuery();
        userCommentActionQuery.setVideoId(videoId);
        assertTargetEmpty(userCommentActionQuery, userCommentActionMapper);
        copyBatch(userCommentActionArchiveList, UserCommentAction::new, userCommentActionMapper);

        userCommentActionArchiveMapper.deleteByParam(userCommentActionArchiveQuery);
        videoCommentArchiveMapper.deleteByParam(videoCommentArchiveQuery);
    }

    /**
     * 彻底清除指定视频的评论归档数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void purgeArchiveByVideoId(long videoId)
    {
        VideoCommentArchiveQuery commentArchiveQuery = new VideoCommentArchiveQuery();
        commentArchiveQuery.setVideoId(videoId);
        videoCommentArchiveMapper.deleteByParam(commentArchiveQuery);

        UserCommentActionArchiveQuery commentActionArchiveQuery = new UserCommentActionArchiveQuery();
        commentActionArchiveQuery.setVideoId(videoId);
        userCommentActionArchiveMapper.deleteByParam(commentActionArchiveQuery);
    }

    /**
     * 同步视频标题到评论冗余字段（含归档）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateVideoNameByVideoId(long videoId, String videoName)
    {
        videoCommentMapper.updateVideoNameByVideoId(videoId, videoName);
        videoCommentArchiveMapper.updateVideoNameByVideoId(videoId, videoName);
    }

    /**
     * 同步视频封面到评论冗余字段（含归档）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateVideoCoverByVideoId(long videoId, String videoCover)
    {
        videoCommentMapper.updateVideoCoverByVideoId(videoId, videoCover);
        videoCommentArchiveMapper.updateVideoCoverByVideoId(videoId, videoCover);
    }

    /**
     * 同步用户昵称到评论冗余字段（含归档）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateNickNameByUserId(long userId, String nickName)
    {
        videoCommentMapper.updateNickNameByUserId(userId, nickName);
        videoCommentArchiveMapper.updateNickNameByUserId(userId, nickName);
        videoCommentMapper.updateReplyNickNameByReplyUserId(userId, nickName);
        videoCommentArchiveMapper.updateReplyNickNameByReplyUserId(userId, nickName);
    }

    /**
     * 同步用户头像到评论冗余字段（含归档）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatarByUserId(long userId, String avatar)
    {
        videoCommentMapper.updateAvatarByUserId(userId, avatar);
        videoCommentArchiveMapper.updateAvatarByUserId(userId, avatar);
    }

    /**
     * 汇总用户评论获赞数
     */
    public Long getUpvoteCountByUserId(long userId)
    {
        Long count = videoCommentMapper.selectUpvoteCountByUserId(userId);
        return count == null ? 0L : count;
    }

    /**
     * 聚合指定统计日内各视频作者收到的评论数（仅未删除）
     *
     * @param statisticsDate 统计日期（自然日）
     */
    public List <CommentDailyStatisticsDTO> getDailyCommentStatistics(LocalDate statisticsDate)
    {
        if (statisticsDate == null)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        LocalDateTime startDate = statisticsDate.atStartOfDay();
        LocalDateTime endDate = statisticsDate.plusDays(1).atStartOfDay();
        List <CommentDailyStatisticsDTO> result = videoCommentMapper.selectDailyCommentCountByVideoUserId(startDate, endDate);
        return result == null ? List.of() : result;
    }

    /**
     * 按层级真正删除评论树<hr/>
     * <p>第一层必须全部已逻辑删除，子层不限制删除标志</p>
     */
    private void destroyCommentTree(List <Long> commentIdList)
    {
        if (commentIdList == null || commentIdList.isEmpty())
        {
            return;
        }

        List <Long> currentLevelCommentIdList = commentIdList.stream().distinct().toList();
        Integer deletedCount = videoCommentMapper.selectDeletedCountByCommentIdList(currentLevelCommentIdList);

        if (deletedCount == null || deletedCount != currentLevelCommentIdList.size())
        {
            throw new BusinessException("存在未逻辑删除或不存在的评论");
        }

        List <VideoComment> commentList = videoCommentMapper.selectBatchByCommentIdList(commentIdList);

        Map <Long, Integer> videoCommentDeleteCountMap = new HashMap <>();
        commentList.forEach(comment ->
                            {
                                if (comment.getVideoId() != null)
                                {
                                    videoCommentDeleteCountMap.merge(comment.getVideoId(), 1, Integer::sum);
                                }
                            });

        List <Long> parentCommentIdList = commentList.stream()
                                                     .map(VideoComment::getParentCommentId)
                                                     .filter(pid -> !Objects.equals(pid, 0L))
                                                     .distinct()
                                                     .toList();

        deletedCount = videoCommentMapper.destroyDeletedByCommentIdList(currentLevelCommentIdList);
        if (deletedCount == null || deletedCount == 0)
        {
            throw new BusinessException("评论未被删除");
        }

        if (!parentCommentIdList.isEmpty())
        {
            videoCommentMapper.decreaseBatchReplyCount(parentCommentIdList, 1);
        }

        while (!currentLevelCommentIdList.isEmpty())
        {
            List <Long> childCommentIdList = videoCommentMapper.selectChildCommentIdList(currentLevelCommentIdList);
            if (childCommentIdList == null || childCommentIdList.isEmpty())
            {
                break;
            }

            List <VideoComment> childCommentList = videoCommentMapper.selectBatchByCommentIdList(childCommentIdList);
            childCommentList.forEach(comment ->
                                     {
                                         if (comment.getVideoId() != null)
                                         {
                                             videoCommentDeleteCountMap.merge(comment.getVideoId(), 1, Integer::sum);
                                         }
                                     });

            Integer deletedChildCount = videoCommentMapper.destroyByCommentIdList(childCommentIdList);
            if (deletedChildCount == null || deletedChildCount == 0)
            {
                break;
            }

            currentLevelCommentIdList = childCommentIdList;
        }

        videoCommentDeleteCountMap.entrySet()
                                  .removeIf(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0);

        if (!videoCommentDeleteCountMap.isEmpty())
        {
            for (Map.Entry <Long, Integer> entry : videoCommentDeleteCountMap.entrySet())
            {
                ResponseVO <Void> decreaseResult = innerVideoFeignClient.decreaseCommentCount(entry.getKey(), entry.getValue());
                if (!ResponseCode.SUCCESS.getCode().equals(decreaseResult.getCode()))
                {
                    throw new BusinessException("更新视频评论数失败");
                }
            }
        }
    }

    private <S, T> void copyBatch(List <S> sourceList, Supplier <T> targetSupplier, BaseMapper <T, ?> targetMapper)
    {
        if (sourceList == null || sourceList.isEmpty())
        {
            return;
        }
        List <T> targetList = sourceList.stream().map(source ->
                                                      {
                                                          T target = targetSupplier.get();
                                                          BeanUtils.copyProperties(source, target);
                                                          return target;
                                                      }).toList();
        targetMapper.insertBatch(targetList);
    }

    private <P> void assertTargetEmpty(P query, BaseMapper <?, P> targetMapper)
    {
        Integer count = targetMapper.selectCount(query);
        if (count != null && count > 0)
        {
            throw new BusinessException("视频无法恢复，因为目标表数据存在冲突，请联系管理员");
        }
    }

    /**
     * 根据videoId获取视频快照<hr/>
     * 自动校验视频是否存在，不存在会抛出异常
     *
     * @param videoId 视频ID
     * @return VideoSnapshotDTO
     */
    private VideoSnapshotDTO getVideoInfo(long videoId)
    {
        ResponseVO <VideoSnapshotDTO> result = innerVideoFeignClient.getVideoSnapshot(videoId);
        if (!ResponseCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        return result.getData();
    }

    /**
     * 获取用户资料快照
     *
     * @param userId 用户ID
     * @return 用户资料，不存在时返回 null
     */
    private UserInfoDTO getUserInfo(long userId)
    {
        ResponseVO <UserInfoDTO> result = innerUserFeignClient.getUserInfo(userId);
        if (!ResponseCode.SUCCESS.getCode().equals(result.getCode()))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        return result.getData();
    }

    /**
     * 发送评论站内信；失败仅记日志，不影响发评主流程
     */
    private void sendCommentMessageSafely(long receiverUserId,
                                          long senderUserId,
                                          long videoId,
                                          String commentContent,
                                          String replyCommentContent,
                                          String warnMessage)
    {
        try
        {
            CommentMessageDTO request = new CommentMessageDTO(receiverUserId,
                                                              senderUserId,
                                                              videoId,
                                                              commentContent,
                                                              replyCommentContent);
            ResponseVO <Void> result = innerUserMessageFeignClient.sendCommentMessage(request);
            if (!ResponseCode.SUCCESS.getCode().equals(result.getCode()))
            {
                log.warn(warnMessage, result.getInfo());
            }
        }
        catch (Exception e)
        {
            log.warn(warnMessage, e.toString());
        }
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
     * 构建评论消息回复区内容
     *
     * @param parentComment 父评论
     * @return 回复消息内容
     */
    private String formatReplyCommentContent(VideoComment parentComment)
    {
        String nickName = parentComment.getNickName();
        // 如果找不到昵称（不太可能），兜底返回UID
        if (nickName == null || nickName.isBlank())
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

    private String formatPostedCommentContent(String content, String imgPaths)
    {
        if (content != null && !content.isBlank())
        {
            return content;
        }

        if (imgPaths != null && !imgPaths.isBlank())
        {
            return "[图片]";
        }

        return "";
    }

    /**
     * <b>分层批量查询评论（批量查询）</b><hr/>
     * <b>旧方案1（递归，每节点单独查）：<br/>
     * <p>
     * </b> 最多 1 + N + N² + ... + N^layer 条 SQL<br/>
     * 以 layer=4, pageSize=10 为例：最多 <b>1111条</b> SQL<br/>
     * </p>
     * <b>旧方案2（分层批量 IN + JOIN）：</b><hr/>
     * <em>置顶评论的特殊对待已经删除，因为现在限制最多置顶5条评论</em><br/>
     * <p>
     * 每个SQL通过IN所有父评论ID，一次性将这些父评论的下一层子评论都扫描出来，<br/>
     * 然后通过窗口函数筛选出前若干条评论，<b>将IO时间复杂度降低为depth</b><br/>
     * 代价是每次扫描出的子评论筛选率太低，<b>用空间换时间</b><br/>
     * </p>
     * <br/>
     *
     * <b>最终方案（每层 LATERAL JOIN）：</b><hr/>
     * <p>
     * 每层只需 1 条 IN 查询，且MySQL只扫描所需行数，不会扫描出整层评论<br/>
     * 每条 SQL 采用 LATERAL JOIN，只扫描每个父评论的前若干条子评论<br/>
     * 不仅消除应用层 N+1 查询，还消除了对整层评论的扫描<br/>
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

        final int childPageSize = commentConfig.getChildrenCommentPageSize();

        // 查询评论
        final int pageSize;
        final int pageIndex;
        List <VideoCommentVO> rootCommentList;

        // 如果是顶级评论
        if (parentCommentId == 0)
        {
            pageSize = commentConfig.getCommentPageSize();
        }
        // 如果是子评论（子评论的有自己的页大小）
        else
        {
            pageSize = commentConfig.getChildrenCommentPageSize();
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
