package com.neon.nilocommon.entity.enums.userCommentAction;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommentActionType
{
    UPVOTE(1), DOWNVOTE(2);

    private final int value;
}
