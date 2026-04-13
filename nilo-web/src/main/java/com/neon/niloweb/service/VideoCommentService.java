package com.neon.niloweb.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoComment.CommentOrderType;
import com.neon.nilocommon.entity.enums.videoInfo.InteractionType;
import com.neon.nilocommon.entity.po.VideoComment;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.PageCalculator;
import com.neon.nilocommon.entity.query.VideoCommentQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.VideoCommentVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.VideoCommentMapper;
import com.neon.niloweb.mapper.VideoInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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
     * @param imgPath         图片路径
     * @param parentCommentId 父级评论ID，如果自己就是顶级评论，则为0
     */
    public void postComment(long userId, long videoId, String content, String imgPath, long parentCommentId)
    {
        VideoInfo videoInfo = getVideoInfo(videoId);

        // 校验是否允许评论
        String interaction = videoInfo.getInteraction();
        if (interaction != null && (interaction.equals(InteractionType.NO_COMMENT.getValue()) || interaction.equals(InteractionType.NO_DANMAKU_AND_COMMENT.getValue())))
        {
            throw new BusinessException("不能评论");
        }

        VideoComment videoComment = new VideoComment();

        // 校验父级评论是否合法
        if (parentCommentId != 0)
        {
            VideoComment parentComment = getVideoComment(parentCommentId, videoId);
            videoComment.setReplyUserId(parentComment.getUserId());
        }

        videoComment.setVideoId(videoId);
        videoComment.setVideoUserId(videoInfo.getUserId());
        if (content != null)
        {
            videoComment.setContent(content);
        }
        if (imgPath != null)
        {
            videoComment.setImgPath(imgPath);
        }
        videoComment.setParentCommentId(parentCommentId);
        videoComment.setUserId(userId);
        videoComment.setPostTime(LocalDateTime.now());
        videoComment.setCommentId(snowflake.nextId());

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
        // todo 以后可以在这里通知被回复者
    }

    /**
     * 获取评论列表<hr/>
     * 获取3层评论，分页大小为10条记录
     *
     * @param videoId         视频ID
     * @param parentCommentId 父级评论ID
     * @param pageNo          页号（从1开始）
     * @param orderType       排序类型
     * @return 评论列表（已按照层级排列好）
     */
    public List <VideoCommentVO> getCommentList(long videoId, long parentCommentId, int pageNo, String orderType, int depth)
    {
        // 校验视频是否存在
        getVideoInfo(videoId);
        if (parentCommentId != 0)
        {
            // 校验父评论是否存在本视频下
            getVideoComment(parentCommentId, videoId);
        }

        if (orderType.equals(CommentOrderType.EARLIEST.getValue()))
        {
            return getVideoCommentListBatch(videoId, parentCommentId, pageNo, "top_type DESC, post_time", depth);
        }
        else if (orderType.equals(CommentOrderType.LATEST.getValue()))
        {
            // 把 top_type 为 1 的放在最前面
            return getVideoCommentListBatch(videoId, parentCommentId, pageNo, "top_type DESC, post_time DESC", depth);
        }
        else if (orderType.equals(CommentOrderType.POPULAR.getValue()))
        {
            return getVideoCommentListBatch(videoId, parentCommentId, pageNo, "top_type DESC, upvote_count DESC", depth);
        }
        else
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
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
     * 分层批量查询评论（批量查询，SQL 总数 = 1 + depth）<hr/>
     * <b>旧方案（递归，每节点单独查）：</b> 最多 2 × (1 + N + N² + ... + N^depth) 条 SQL<br/>
     * 以 depth=3, pageSize=10 为例：最多 <b>2222条</b> SQL<br/>
     * <b>新方案（分层批量 IN）：</b> 固定 2 + depth 条 SQL<br/>
     * 以 depth=3 为例：固定 <b>4条</b> SQL（第1层 count，第1~3层各1条 IN 查询）
     *
     * @param videoId         视频ID
     * @param parentCommentId 起始父评论ID（0 = 顶层）
     * @param pageNo          页号（仅对顶层有效）
     * @param orderCommand    顶层排序 SQL 片段
     * @param depth           查询层数（包括顶层）
     * @return 已组装好层级结构的顶层 VO 列表
     */
    private List <VideoCommentVO> getVideoCommentListBatch(long videoId, long parentCommentId, int pageNo, String orderCommand, int depth)
    {
        // ===== 2次SQL查询：带分页查询顶层评论 =====
        VideoCommentQuery query = new VideoCommentQuery();
        query.setParentCommentId(parentCommentId);
        query.setVideoId(videoId);
        query.setOrderBy(orderCommand);
        Integer count = videoCommentMapper.selectCount(query);
        query.setPageCalculator(new PageCalculator(pageNo, count, webConfig.getCommentPageSize()));
        List <VideoComment> rootComments = videoCommentMapper.selectList(query);

        if (rootComments == null || rootComments.isEmpty())
        {
            return new ArrayList <>();
        }

        int limit = webConfig.getChildrenCommentPageSize();

        // 建立 commentId → VO 映射，便于通过parentCommentId查找VO，从而给父VO挂载子评论
        Map <Long, VideoCommentVO> voMap = new HashMap <>();
        for (VideoComment vc : rootComments)
        {
            voMap.put(vc.getCommentId(), toVO(vc, limit));
        }

        // 当前层的所有 comment_id，作为下一层 IN 查询的条件
        List <Long> currentParentIdList = new ArrayList <>(voMap.keySet());

        // ===== depth次SQL查询：每层只需 1 条 IN 查询=====
        for (int d = 1 ; d < depth ; d++)
        {
            if (currentParentIdList.isEmpty())
            {
                break;
            }

            List <VideoComment> children = videoCommentMapper.selectByParentIdList(currentParentIdList, videoId, limit);
            if (children == null || children.isEmpty())
            {
                break;
            }

            // 按父节点分组
            Map <Long, List <VideoCommentVO>> childrenByParent = new HashMap <>();
            for (VideoComment child : children)
            {
                VideoCommentVO vo = toVO(child, limit);
                // 如果到了搜索的最后一层，它如果还有子节点，不管和limit的数量关系怎么样，也展示不了，所以如果它还有子节点，必须把它的hasMoreChildren设置为true
                if (d == depth - 1)
                {
                    vo.setHasMoreChildren(child.getReplyCount() != null && child.getReplyCount() > 0);
                }
                childrenByParent.computeIfAbsent(child.getParentCommentId(), k -> new ArrayList <>()).add(vo);
            }

            // 挂载到父节点（hasMoreChildren 已由 toVO 设置好，无需再次判断）
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
     * 将 VideoComment PO 转换为 VideoCommentVO，并根据 replyCount 设置 hasMoreChildren。
     *
     * @param vc    VideoComment PO
     * @param limit 每个父节点的子评论展示上限
     * @return VideoCommentVO（hasMoreChildren 已设置）
     */
    private VideoCommentVO toVO(VideoComment vc, int limit)
    {
        VideoCommentVO vo = new VideoCommentVO();
        BeanUtils.copyProperties(vc, vo);
        // replyCount 是数据库里维护的直接子评论精确计数，直接用于判断是否有更多
        vo.setHasMoreChildren(vc.getReplyCount() != null && vc.getReplyCount() > limit);
        return vo;
    }
}
