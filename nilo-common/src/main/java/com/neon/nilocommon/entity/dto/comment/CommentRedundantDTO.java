package com.neon.nilocommon.entity.dto.comment;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentRedundantDTO
{
    private Long videoId;

    private String videoName;

    private String videoCover;

    private Long userId;

    private String nickName;

    private String avatar;
}
