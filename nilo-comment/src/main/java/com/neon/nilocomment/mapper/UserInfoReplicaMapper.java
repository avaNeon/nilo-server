package com.neon.nilocomment.mapper;

import com.neon.nilocommon.entity.dto.UserSnapshotDTO;
import org.apache.ibatis.annotations.Param;

/**
 * 用户资料快照
 */
public interface UserInfoReplicaMapper
{
    /**
     * 新增或覆盖用户快照（canal 监听到 user_info 新增、昵称或头像更新后触发），重复执行结果相同
     */
    Integer upsert(@Param("user") UserSnapshotDTO user);

    /**
     * 删除用户快照（用户注销时触发），行不存在时什么都不做
     */
    Integer deleteByUserId(@Param("userId") long userId);

    /**
     * 按用户ID读取昵称，不存在时返回 null
     */
    String selectNickNameByUserId(@Param("userId") long userId);
}
