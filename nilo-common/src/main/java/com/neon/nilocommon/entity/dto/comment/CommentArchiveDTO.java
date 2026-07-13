package com.neon.nilocommon.entity.dto.comment;

import com.neon.nilocommon.entity.enums.comment.OperationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CommentArchiveDTO
{
    private Long videoId;

    OperationType operationType;
}
