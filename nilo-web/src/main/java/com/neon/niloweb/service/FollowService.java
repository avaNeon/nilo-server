package com.neon.niloweb.service;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.FollowInfo;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.query.FollowInfoQuery;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.vo.userInfo.FollowUserInfo;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.niloweb.mapper.FollowInfoMapper;
import com.neon.niloweb.mapper.UserInfoMapper;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class FollowService
{
    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final FollowInfoMapper <FollowInfo, FollowInfoQuery> followInfoMapper;

    private final AccountRedisRepository accountRedisRepository;

    /**
     * 关注/取消关注
     *
     * @param followerUserId  关注者ID
     * @param followingUserId 被关注人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void followOperation(long followerUserId, long followingUserId)
    {
        // 不能关注自己
        if (followerUserId == followingUserId)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }

        // 先校验用户是否存在
        checkUserExists(followerUserId);
        checkUserExists(followingUserId);

        // 再校验是否关注过此用户
        FollowInfo followInfo = followInfoMapper.selectByFollowerUserIdAndFollowingUserId(followerUserId, followingUserId);
        if (followInfo != null)
        {
            followInfoMapper.deleteByFollowerUserIdAndFollowingUserId(followerUserId, followingUserId);
        }
        else
        {
            followInfoMapper.insert(new FollowInfo(followerUserId, followingUserId, LocalDateTime.now()));
        }

        // 删除redis记录，保证数据是最新的
        accountRedisRepository.deleteUserStateBatch(List.of(followerUserId, followingUserId));
    }

    /**
     * 获取粉丝列表
     *
     * @param followingUserId 用户ID
     * @param pageNo          页号
     * @param pageSize        页大小
     * @return 粉丝列表
     */
    public List <FollowUserInfo> getFollowerList(long followingUserId, int pageNo, int pageSize)
    {
        Integer followerCount = followInfoMapper.selectFollowerCount(followingUserId);
        if (followerCount == 0)
        {
            return new ArrayList <>();
        }

        FollowInfoQuery followInfoQuery = new FollowInfoQuery();
        followInfoQuery.setFollowingUserId(followingUserId);
        followInfoQuery.setOrderBy("f.follow_time DESC");
        followInfoQuery.setPageCalculator(new PageCalculator(pageNo, followerCount, pageSize));
        List <FollowInfo> followInfoList = followInfoMapper.selectList(followInfoQuery);
        if (followInfoList.isEmpty())
        {
            return new ArrayList <>();
        }

        List <Long> followerUserIdList = followInfoList.stream().map(FollowInfo::getFollowerUserId).toList();
        List <UserInfo> followerUserInfoList = userInfoMapper.selectBatchByUserId(followerUserIdList);

        return buildFollowUserInfoList(followingUserId, followerUserIdList, followerUserInfoList);
    }

    /**
     * 获取关注列表
     *
     * @param followerUserId 用户ID
     * @param pageNo         页号
     * @param pageSize       页大小
     * @return 粉丝列表
     */
    public List <FollowUserInfo> getFollowingList(long followerUserId, int pageNo, int pageSize)
    {
        Integer followingCount = followInfoMapper.selectFollowingCount(followerUserId);
        if (followingCount == 0)
        {
            return new ArrayList <>();
        }

        FollowInfoQuery followInfoQuery = new FollowInfoQuery();
        followInfoQuery.setFollowerUserId(followerUserId);
        followInfoQuery.setOrderBy("f.follow_time DESC");
        followInfoQuery.setPageCalculator(new PageCalculator(pageNo, followingCount, pageSize));
        List <FollowInfo> followInfoList = followInfoMapper.selectList(followInfoQuery);
        if (followInfoList.isEmpty())
        {
            return new ArrayList <>();
        }

        List <Long> followingUserIdList = followInfoList.stream().map(FollowInfo::getFollowingUserId).toList();
        List <UserInfo> followingUserInfoList = userInfoMapper.selectBatchByUserId(followingUserIdList);
        return buildFollowUserInfoList(followerUserId, followingUserIdList, followingUserInfoList);
    }

//    /**
//     * 获取指定用户粉丝数
//     *
//     * @param userId 用户ID
//     * @return 粉丝数
//     */
//    public Integer getFollowerCount(long userId)
//    {
//        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
//        if (userInfo == null)
//        {
//            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
//        }
//        return followInfoMapper.selectFollowerCount(userId);
//    }

    /**
     * 检查用户是否存在于MySQL记录中
     *
     * @param userId 用户ID
     */
    private void checkUserExists(long userId)
    {
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        if (userInfo == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }
    }

    private List <FollowUserInfo> buildFollowUserInfoList(long currentUserId,
                                                          List <Long> targetUserIdList,
                                                          List <UserInfo> targetUserInfoList)
    {
        if (targetUserIdList.isEmpty())
        {
            return new ArrayList <>();
        }

        Set <Long> followedUserIdSet = followInfoMapper.selectByFollowerUserIdAndFollowingUserIdList(currentUserId,
                                                                                                     targetUserIdList)
                                                       .stream()
                                                       .map(FollowInfo::getFollowingUserId)
                                                       .collect(Collectors.toSet());

        Set <Long> followingUserIdSet = followInfoMapper.selectByFollowerUserIdListAndFollowingUserId(targetUserIdList,
                                                                                                      currentUserId)
                                                        .stream()
                                                        .map(FollowInfo::getFollowerUserId)
                                                        .collect(Collectors.toSet());

        Map <Long, UserInfo> targetUserInfoMap = targetUserInfoList.stream()
                                                                   .collect(Collectors.toMap(UserInfo::getUserId,
                                                                                             userInfo -> userInfo));
        return targetUserIdList.stream().map(targetUserInfoMap::get).filter(Objects::nonNull).map(userInfo ->
                                                                                                  {
                                                                                                      Long targetUserId = userInfo.getUserId();

                                                                                                      FollowUserInfo followUserInfo = new FollowUserInfo();
                                                                                                      BeanUtils.copyProperties(
                                                                                                              userInfo,
                                                                                                              followUserInfo);
                                                                                                      followUserInfo.setFollowed(
                                                                                                              followedUserIdSet.contains(
                                                                                                                      targetUserId));
                                                                                                      followUserInfo.setFollowing(
                                                                                                              followingUserIdSet.contains(
                                                                                                                      targetUserId));

                                                                                                      return followUserInfo;
                                                                                                  }).toList();
    }
}
