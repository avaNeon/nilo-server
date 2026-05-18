package com.neon.nilocommon.entity.vo.comment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.neon.nilocommon.entity.po.UserCommentAction;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
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
    private String imgPaths;

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
     * 直接子评论数（仅统计下一层，不含子评论的子评论）<br/>
     * 用于前端显示"查看 N 条回复"，以及判断 hasMoreChildren
     */
    private Integer replyCount;

    /**
     * 逻辑删除标记：0-未删除，1-已删除
     */
    private Integer deleted;

    /**
     * 子评论列表
     */
    private List <VideoCommentVO> childCommentList;

    /**
     * 是否还有更多子评论未展示（用于前端渲染"查看更多回复"入口）
     */
    private boolean hasMoreChildren;

    /**
     * 用户是否点赞
     */
    @JsonProperty("isUpvoted")
    private boolean isUpvoted;

    /**
     * 用户是否点踩
     */
    @JsonProperty("isDownvoted")
    private boolean isDownvoted;

    /**
     * 评论者昵称（关联 user_info.nick_name）
     */
    private String nickName;

    /**
     * 评论者头像（关联 user_info.avatar）
     */
    private String avatar;

    /**
     * 当前查询用户对该评论的操作记录（null 表示未登录或尚未操作）<br/>
     * 由 SQL 关联 user_comment_action 表得到，Service 层据此设置 isUpvoted / isDownvoted。
     */
    private UserCommentAction currentUserAction;

}
