package com.neon.niloadmin.service;

import com.neon.niloadmin.mapper.VideoCommentMapper;
import com.neon.niloadmin.mapper.VideoInfoMapper;
import com.neon.nilocommon.entity.enums.videoComment.DeleteType;
import com.neon.nilocommon.entity.po.VideoComment;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoCommentQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.comment.CommentManagementAdmin;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
public class VideoCommentService
{
    private final VideoCommentMapper <VideoComment, VideoCommentQuery> videoCommentMapper;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    /**
     * 分页查询评论列表。
     */
    public Long getCommentManagementInfoCount(String nameFuzzy)
    {
        if (nameFuzzy == null || nameFuzzy.isBlank())
        {
            nameFuzzy = null;
        }
        return videoCommentMapper.selectCommentManagementCount(nameFuzzy);
    }

    public List <CommentManagementAdmin> getCommentManagementInfo(String nameFuzzy, int pageNo, int pageSize)
    {
        if (nameFuzzy == null || nameFuzzy.isBlank())
        {
            nameFuzzy = null;
        }
        int start = (pageNo - 1) * pageSize;
        return videoCommentMapper.selectCommentManagement(nameFuzzy, start, pageSize);
    }

    /**
     * 逻辑删除指定评论。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(long commentId)
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

        if (deletedCount != null && deletedCount > 0)
        {
            videoInfoMapper.decreaseByField(videoComment.getVideoId(), "comment_count", 1);
        }
        else
        {
            throw new BusinessException("评论未被删除");
        }
    }

    /**
     * 真正删除指定已逻辑删除的评论。
     */
    @Transactional(rollbackFor = Exception.class)
    public void destroyComment(long commentId)
    {
        destroyCommentTree(List.of(commentId));
    }

    /**
     * 真正删除发布时间在指定日期范围内且已逻辑删除的评论
     *
     * @param postTimeStart 开始时间
     * @param postTimeEnd   结束时间（均包含）
     */
    @Transactional(rollbackFor = Exception.class)
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
     * 按层级真正删除评论树<hr/>
     * <p>第一层必须全部已逻辑删除，子层不限制删除标志</p>
     */
    private void destroyCommentTree(List <Long> commentIdList)
    {
        // 不能为空
        if (commentIdList == null || commentIdList.isEmpty())
        {
            return;
        }

        List <Long> currentLevelCommentIdList = commentIdList.stream().distinct().toList();
        Integer deletedCount = videoCommentMapper.selectDeletedCountByCommentIdList(currentLevelCommentIdList);

        // 校验第一层是否都是删除位非0
        if (deletedCount == null || deletedCount != currentLevelCommentIdList.size())
        {
            throw new BusinessException("存在未逻辑删除或不存在的评论");
        }

        deletedCount = videoCommentMapper.destroyDeletedByCommentIdList(currentLevelCommentIdList);
        // 确保都被删除
        if (deletedCount == null || deletedCount == 0)
        {
            throw new BusinessException("评论未被删除");
        }

        // 循环删除每层子评论
        while (!currentLevelCommentIdList.isEmpty())
        {
            List <Long> childCommentIdList = videoCommentMapper.selectChildCommentIdList(currentLevelCommentIdList);
            if (childCommentIdList == null || childCommentIdList.isEmpty())
            {
                break;
            }

            childCommentIdList = childCommentIdList.stream().distinct().toList();
            Integer deletedChildCount = videoCommentMapper.destroyByCommentIdList(childCommentIdList);
            if (deletedChildCount == null || deletedChildCount == 0)
            {
                break;
            }
            currentLevelCommentIdList = childCommentIdList;
        }
    }

}
