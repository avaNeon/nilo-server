package com.neon.niloweb.service;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userCommentAction.CommentActionType;
import com.neon.nilocommon.entity.enums.videoComment.DeleteType;
import com.neon.nilocommon.entity.po.UserCommentAction;
import com.neon.nilocommon.entity.po.VideoComment;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.UserCommentActionQuery;
import com.neon.nilocommon.entity.query.VideoCommentQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.UserCommentActionVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.mapper.UserCommentActionMapper;
import com.neon.niloweb.mapper.VideoCommentMapper;
import com.neon.niloweb.mapper.VideoInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class UserCommentActionService
{
    private final UserCommentActionMapper <UserCommentAction, UserCommentActionQuery> userCommentActionMapper;

    private final VideoCommentMapper <VideoComment, VideoCommentQuery> videoCommentMapper;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

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

        // 检查视频是否存在
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        if (videoInfo == null)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        userCommentAction.setVideoId(videoId);

        // 检查评论是否存在
        VideoComment videoComment = videoCommentMapper.selectByCommentId(commentId);
        if (videoComment == null || videoComment.getDeleted() != DeleteType.UNDELETED.getValue())
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
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
            else
            {
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

    /**
     * 获取用户对评论的操作
     *
     * @param userId    用户ID
     * @param commentId 评论ID
     * @return 所有用户对评论的操作
     */
    public List <UserCommentActionVO> getCommentAction(long userId, long commentId)
    {
        return null;
    }
}
