package com.neon.nilocomment.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 用户资料快照
 */
public interface UserInfoReplicaMapper
{
    /**
     * 按用户ID读取昵称，不存在时返回 null
     */
    String selectNickNameByUserId(@Param("userId") long userId);

    /**
     * 同步用户昵称（web 端 user_info.nick_name 变更时调用，与 web 端方法处于同一个 Seata AT 全局事务中）
     */
    Integer updateNickNameByUserId(@Param("userId") long userId, @Param("nickName") String nickName);

    /**
     * 同步用户头像（web 端 user_info.avatar 变更时调用，与 web 端方法处于同一个 Seata AT 全局事务中）
     */
    Integer updateAvatarByUserId(@Param("userId") long userId, @Param("avatar") String avatar);
}
