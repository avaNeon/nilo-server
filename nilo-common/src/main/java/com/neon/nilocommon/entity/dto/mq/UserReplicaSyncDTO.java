package com.neon.nilocommon.entity.dto.mq;

import com.neon.nilocommon.entity.dto.UserSnapshotDTO;
import com.neon.nilocommon.entity.enums.comment.ReplicaSyncType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 通知评论服务同步用户副本<hr/>
 * 新增和删除走同一个队列，保证同一个用户的操作按发生顺序执行
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UserReplicaSyncDTO
{
    private ReplicaSyncType type;

    /**
     * 删除时只用到 userId
     */
    private UserSnapshotDTO user;
}
