package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;


/**
 * 评论
 */
@Getter
@Setter
@ToString
public class VideoComment
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
     * 视频标题（冗余自 video_info.video_name，最终一致）
     */
    private String videoName;

    /**
     * 视频封面（冗余自 video_info.video_cover，最终一致）
     */
    private String videoCover;

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
     * 评论者昵称（冗余自 user_info.nick_name，最终一致）
     */
    private String nickName;

    /**
     * 评论者头像（冗余自 user_info.avatar，最终一致）
     */
    private String avatar;

    /**
     * 回复人ID
     */
    private Long replyUserId;

    /**
     * 被回复者昵称（冗余，最终一致；顶层评论可为 null）
     */
    private String replyNickName;

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
     * 直接子评论数（仅统计下一层，不含子评论的子评论）
     */
    private Integer replyCount;

    /**
     * 逻辑删除标记：0 未删除，1 已被用户删除，2 被视频制作者删除
     */
    private Integer deleted;

}
