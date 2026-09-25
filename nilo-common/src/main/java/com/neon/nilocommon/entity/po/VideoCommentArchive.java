package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;


/**
 * 评论存档
 */
@Getter
@Setter
@ToString
public class VideoCommentArchive
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
     * 视频ID
     */
    private Long videoId;

    /**
     * 视频用户ID
     */
    private Long videoUserId;

    /**
     * 回复内容
     */
    private String content;

    /**
     * 图片路径
     */
    private String imgPaths;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 回复人ID
     */
    private Long replyUserId;

    /**
     * 0:未置顶 1:置顶
     */
    private Integer topType;

    /**
     * 发布时间
     */
    private LocalDateTime postTime;

    /**
     * 点赞数量
     */
    private Integer upvoteCount;

    /**
     * 点踩数量
     */
    private Integer downvoteCount;

    /**
     * （下一层）回复数量
     */
    private Integer replyCount;

    /**
     * 删除标记（0：未删除，1：已删除）
     */
    private Integer deleted;

}
