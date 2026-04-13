package com.neon.nilocommon.entity.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@ToString
public class VideoCommentVO
{
    /**
     * 评论ID【对外展示】
     */
    private Long commentId;

    /**
     * 父级评论ID（为0表示顶层评论）
     */
    private Long parentCommentId;

    /**
     * 回复内容
     */
    private String content;

    /**
     * 图片路径
     */
    private String imgPath;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 0:未置顶 1:置顶
     */
    private Integer topType;

    /**
     * 发布时间
     */
    private Date postTime;

    /**
     * 点赞数量
     */
    private Integer upvoteCount;

    /**
     * 点踩数量
     */
    private Integer downvoteCount;

    /**
     * 直接子评论数（仅统计下一层，不含子评论的子评论）<br/>
     * 用于前端显示"查看 N 条回复"，以及判断 hasMoreChildren
     */
    private Integer replyCount;

    /**
     * 子评论列表
     */
    private List<VideoCommentVO> childCommentList;

    /**
     * 是否还有更多子评论未展示（用于前端渲染"查看更多回复"入口）
     */
    private boolean hasMoreChildren;

}
