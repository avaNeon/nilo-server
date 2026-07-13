package com.neon.nilocomment.service;

import com.neon.nilocomment.mapper.UserCommentActionMapper;
import com.neon.nilocomment.mapper.VideoCommentMapper;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userCommentAction.CommentActionType;
import com.neon.nilocommon.entity.enums.videoComment.DeleteType;
import com.neon.nilocommon.entity.po.UserCommentAction;
import com.neon.nilocommon.entity.po.VideoComment;
import com.neon.nilocommon.entity.query.UserCommentActionQuery;
import com.neon.nilocommon.entity.query.VideoCommentQuery;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class UserCommentActionService
{
    private final UserCommentActionMapper <UserCommentAction, UserCommentActionQuery> userCommentActionMapper;

    private final VideoCommentMapper <VideoComment, VideoCommentQuery> videoCommentMapper;

    /**
     * 对评论进行操作
     *
     * @param userId     用户ID
     * @param videoId    视频ID
     * @param commentId  评论ID
     * @param actionType 操作类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void commentAction(long userId, long videoId, long commentId, int actionType)
    {
        UserCommentAction userCommentAction = new UserCommentAction();
        userCommentAction.setUserId(userId);

        // 检查评论是否存在
        VideoComment videoComment = videoCommentMapper.selectByCommentId(commentId);
        if (videoComment == null || videoComment.getDeleted() != DeleteType.UNDELETED.getValue() || !Objects.equals(videoId,
                                                                                                                    videoComment.getVideoId()))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        userCommentAction.setVideoId(videoId);
        userCommentAction.setCommentId(commentId);

        userCommentAction.setActionType(actionType);
        userCommentAction.setActionTime(LocalDateTime.now());

        if (actionType == CommentActionType.UPVOTE.getValue() || actionType == CommentActionType.DOWNVOTE.getValue())
        {
            int oppositeType = actionType == CommentActionType.UPVOTE.getValue() ? CommentActionType.DOWNVOTE.getValue() : CommentActionType.UPVOTE.getValue();
            UserCommentAction oppositeAction = userCommentActionMapper.selectByCommentIdAndUserIdAndActionType(commentId,
                                                                                                               userId,
                                                                                                               oppositeType);
            UserCommentAction dbAction = userCommentActionMapper.selectByCommentIdAndUserIdAndActionType(commentId,
                                                                                                         userId,
                                                                                                         actionType);

            // 存在相同记录的情况，删除相同的记录
            if (dbAction != null)
            {
                userCommentActionMapper.deleteByCommentIdAndUserIdAndActionType(commentId, userId, actionType);
                if (actionType == CommentActionType.UPVOTE.getValue())
                {
                    videoCommentMapper.decreaseUpVoteCount(commentId);
                }
                else
                {
                    videoCommentMapper.decreaseDownvoteCount(commentId);
                }
            }
            // 否则，需要新增记录
            else
            {
                // 先判断数据库有没有对立的记录，如果有，那么删除这条记录
                if (oppositeAction != null)
                {
                    userCommentActionMapper.deleteByCommentIdAndUserIdAndActionType(commentId, userId, oppositeType);
                    if (actionType == CommentActionType.UPVOTE.getValue())
                    {
                        videoCommentMapper.decreaseDownvoteCount(commentId);
                    }
                    else
                    {
                        videoCommentMapper.decreaseUpVoteCount(commentId);
                    }
                }
                userCommentActionMapper.insert(userCommentAction);

                if (actionType == CommentActionType.UPVOTE.getValue())
                {
                    videoCommentMapper.increaseUpvoteCount(commentId);
                }
                else
                {
                    videoCommentMapper.increaseDownvoteCount(commentId);
                }
            }
        }
        // 错误的 action_type
        else
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
    }

}
