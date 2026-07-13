package com.neon.nilocommon.entity.query;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


/**
 * 评论参数
 */
@Setter
@Getter
@ToString
public class VideoCommentQuery extends BaseQuery
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
     * 视频标题（冗余）
     */
    private String videoName;

    private String videoNameFuzzy;

    /**
     * 视频封面（冗余）
     */
    private String videoCover;

    private String videoCoverFuzzy;

    /**
     * 视频用户ID
     */
    private Long videoUserId;

    /**
     * 回复内容
     */
    private String content;

    private String contentFuzzy;

    /**
     * 图片路径
     */
    private String imgPaths;

    private String imgPathsFuzzy;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 评论者昵称（冗余）
     */
    private String nickName;

    private String nickNameFuzzy;

    /**
     * 评论者头像（冗余）
     */
    private String avatar;

    private String avatarFuzzy;

    /**
     * 回复人ID
     */
    private Long replyUserId;

    /**
     * 被回复者昵称（冗余）
     */
    private String replyNickName;

    private String replyNickNameFuzzy;

    /**
     * 0:未置顶 1:置顶
     */
    private Integer topType;

    /**
     * 发布时间
     */
    private String postTime;

    private String postTimeStart;

    private String postTimeEnd;

    /**
     * 点赞数量
     */
    private Integer upvoteCount;

    /**
     * 点踩数量
     */
    private Integer downvoteCount;

    /**
     * 逻辑删除标记：0-未删除，1-已删除
     */
    private Integer deleted;

}
