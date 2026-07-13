package com.neon.nilocommon.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论相关站内信请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentMessageDTO
{
    @NotNull
    private Long receiverUserId;

    @NotNull
    private Long senderUserId;

    @NotNull
    private Long videoId;

    private String commentContent;

    private String replyCommentContent;
}
