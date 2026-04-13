package com.neon.nilocommon.entity.enums.videoComment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommentOrderType
{
    LATEST("latest"), POPULAR("popular");

    private final String value;
}
