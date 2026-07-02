package com.neon.nilocommon.entity.vo.comment;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class CommentManagementAdmin
{
    // video_comment
    private Long commentId;
    private Long userId;
    private Long replyUserId;
    private String content;
    private String imgPaths;
    private LocalDateTime postTime;
    private Long videoId;
    /**
     * 逻辑删除标记：0-未删除，1-已删除
     */
    private Integer deleted;
    // video_info
    private String videoName;
    private String videoCover;
    // user_info
    private String nickName;
    private String replyNickName;
    private String avatar;
}