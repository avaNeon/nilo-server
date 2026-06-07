package com.neon.niloweb.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据库操作接口
 */
public interface FollowInfoMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 获取粉丝数
     *
     * @param followingUserId 被关注者用户ID
     * @return 粉丝数
     */
    Integer selectFollowerCount(@Param("followingUserId") Long followingUserId);

    /**
     * 获取关注数
     *
     * @param followerUserId 粉丝用户ID
     * @return 关注数
     */
    Integer selectFollowingCount(@Param("followerUserId") Long followerUserId);

    /**
     * 根据FollowerUserIdAndFollowingUserId更新
     */
    Integer updateByFollowerUserIdAndFollowingUserId(@Param("bean") T t,
                                                     @Param("followerUserId") Long followerUserId,
                                                     @Param("followingUserId") Long followingUserId);


    /**
     * 根据FollowerUserIdAndFollowingUserId删除
     */
    Integer deleteByFollowerUserIdAndFollowingUserId(@Param("followerUserId") Long followerUserId,
                                                     @Param("followingUserId") Long followingUserId);


    /**
     * 根据FollowerUserIdAndFollowingUserId获取对象
     */
    T selectByFollowerUserIdAndFollowingUserId(@Param("followerUserId") Long followerUserId,
                                               @Param("followingUserId") Long followingUserId);

    /**
     * 查询指定用户关注的目标用户列表
     */
    List <T> selectByFollowerUserIdAndFollowingUserIdList(@Param("followerUserId") Long followerUserId,
                                                          @Param("followingUserIdList") List <Long> followingUserIdList);

    /**
     * 查询关注指定用户的来源用户列表
     */
    List <T> selectByFollowerUserIdListAndFollowingUserId(@Param("followerUserIdList") List <Long> followerUserIdList,
                                                          @Param("followingUserId") Long followingUserId);
}
