package com.neon.nilocommon.entity.vo;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class CommentManagementVO
{
    // video_comment
    private Long commentId;
    private Long userId;
    private Long replyUserId;
    private String content;
    private String imgPaths;
    private LocalDateTime postTime;
    private Long videoId;
    // video_info
    private String videoName;
    private String videoCover;
    // user_info
    private String nickName;
    private String replyNickName;
    private String avatar;
}
