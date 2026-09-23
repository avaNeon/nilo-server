package com.neon.nilocommon.entity.dto.comment;

import com.neon.nilocommon.entity.enums.comment.OperationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 走 MQ 的 DTO 必须有无参构造器：消费端的 Jackson 要先 new 一个空对象再用 setter 填字段
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CommentArchiveDTO
{
    private Long videoId;

    OperationType operationType;
}
