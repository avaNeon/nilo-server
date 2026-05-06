package com.neon.niloweb.service;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userVideoAction.VideoActionType;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.po.UserState;
import com.neon.nilocommon.entity.po.UserVideoAction;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.query.UserVideoActionQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.UserVideoActionVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.EnumFieldChecker;
import com.neon.niloweb.mapper.UserInfoMapper;
import com.neon.niloweb.mapper.UserVideoActionMapper;
import com.neon.niloweb.mapper.VideoInfoMapper;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class UserVideoActionService
{
    private static final int expireDays = 7;

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final UserVideoActionMapper <UserVideoAction, UserVideoActionQuery> userVideoActionMapper;

    private final AccountRedisRepository accountRedisRepository;

    /**
     * 视频操作记录
     *
     * @param userId     用户ID
     * @param videoId    视频ID
     * @param actionType 操作类型
     * @param coinAmount 如果是0，代表不是投币操作
     */
    @Transactional(rollbackFor = Exception.class)
    public void videoAction(long userId, long videoId, int actionType, short coinAmount)
    {
        UserVideoAction userVideoAction = new UserVideoAction();
        userVideoAction.setUserId(userId);
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);

        // 不能对不存在的视频操作
        if (videoInfo == null)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        userVideoAction.setVideoId(videoId);
        userVideoAction.setVideoUserId(videoInfo.getUserId());

        // 校验操作类型合法性
        if (!EnumFieldChecker.containsFieldValue(VideoActionType.class, "value", actionType))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        userVideoAction.setActionType(actionType);

        // 校验投币合法性
        if (userVideoAction.getActionType() == VideoActionType.COIN.getValue())
        {
            // 不能个数为0
            if (coinAmount == 0)
            {
                throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
            }
            // 不能给自己的视频投币
            if (videoInfo.getUserId() == userId)
            {
                throw new BusinessException("无法向自己投币");
            }
        }
        // 要不是投币操作，coinAmount必须是0，防止别人瞎搞
        else
        {
            coinAmount = 0;
        }

        userVideoAction.setCoinAmount(coinAmount);

        UserVideoActionQuery query = new UserVideoActionQuery();
        query.setUserId(userId);
        query.setVideoId(videoId);
        query.setActionType(actionType);
        List <UserVideoAction> dbUserVideoActionList = userVideoActionMapper.selectList(query);

        // 新增
        if (dbUserVideoActionList == null || dbUserVideoActionList.isEmpty())
        {
            // actionId 不必设置，这个是自增主键
            userVideoAction.setActionTime(LocalDateTime.now());
            userVideoActionMapper.insert(userVideoAction);

            // todo 更新ES
            switch (actionType)
            {
                // LIKE
                case 1 -> videoInfoMapper.increaseLikeCount(videoId);
                // SAVE
                // todo添加到收藏夹操作
                case 2 -> videoInfoMapper.increaseCollectCount(videoId);
                // COIN
                case 3 ->
                {
                    videoInfoMapper.increaseCoinCount(videoId, coinAmount);
                    Integer updatedLine = userInfoMapper.decreaseCoin(userId, coinAmount);
                    if (updatedLine == 0)
                    {
                        throw new BusinessException("硬币余额不足");
                    }
                    userInfoMapper.increaseCoin(videoInfo.getUserId(), coinAmount);
                    // 更新用户统计缓存信息
                    // 更新本用户
                    updateUserState(userId);
                    // 更新视频发布者
                    updateUserState(videoInfo.getUserId());
                }
                // UNKNOWN
                default -> throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
            }
        }
        // 存在相同操作
        else if (dbUserVideoActionList.size() == 1)
        {
            UserVideoAction dbUserVideoAction = dbUserVideoActionList.get(0);
            // 如果是投币
            if (actionType == VideoActionType.COIN.getValue())
            {
                int totalCoin = dbUserVideoAction.getCoinAmount() + coinAmount;
                // 如果总投币数量不合法，就抛出异常
                if (totalCoin > 2) // 为投币数量超过上限的条件额外加一个提示信息吧
                {
                    throw new BusinessException("投币数量超过上限");
                }
                else if (totalCoin != 1 && totalCoin != 2)
                {
                    throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
                }
                // 否则就更新投币数量
                else
                {
                    dbUserVideoAction.setCoinAmount((short) totalCoin);
                    dbUserVideoAction.setActionTime(LocalDateTime.now()); // 别忘记时间也要更新
                    // 更新user_video_action
                    userVideoActionMapper.updateByActionId(dbUserVideoAction, dbUserVideoAction.getActionId());
                    // 更新video_info
                    videoInfoMapper.increaseCoinCount(videoId, coinAmount);
                    // 更新user_info
                    Integer updatedLine = userInfoMapper.decreaseCoin(userId, coinAmount);
                    if (updatedLine == 0)
                    {
                        throw new BusinessException("硬币余额不足");
                    }
                    userInfoMapper.increaseCoin(videoInfo.getUserId(), coinAmount);
                    // 更新用户统计缓存信息
                    // 更新本用户
                    updateUserState(userId);
                    // 更新视频发布者
                    updateUserState(videoInfo.getUserId());
                }
            }
            // 点赞、收藏
            else
            {
                // 点赞、收藏过了，就删除
                // 更新user_video_action
                userVideoActionMapper.deleteByActionId(dbUserVideoAction.getActionId());
                // 更新video_info
                // todo 更新ES收藏、点赞
                switch (actionType)
                {
                    // CANCEL THE LIKE
                    case 1 -> videoInfoMapper.decreaseLikeCount(videoId);
                    // CANCEL THE SAVE
                    // todo 添加到收藏夹
                    case 2 -> videoInfoMapper.decreaseCollectCount(videoId);
                    // CANCEL THE REQUEST!!!
                    default -> throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
                }
            }
        }
        // 如果不止一条，那就是很奇怪的问题了...
        else
        {
            throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
        }
    }

    /**
     * 获取用户的视频操作
     *
     * @param userId  用户ID
     * @param videoId 视频ID
     * @return 用户的所有对指定视频的操作
     */
    public List <UserVideoActionVO> getVideoAction(long userId, long videoId)
    {
        List <UserVideoAction> userVideoActionList = userVideoActionMapper.selectByVideoIdAndUserId(videoId, userId);
        if (userVideoActionList == null || userVideoActionList.isEmpty())
        {
            return null;
        }
        else
        {
            return userVideoActionList.stream().map(userVideoAction ->
                                                    {
                                                        UserVideoActionVO userVideoActionVO = new UserVideoActionVO();
                                                        userVideoActionVO.setActionType(userVideoAction.getActionType());
                                                        userVideoActionVO.setCoinAmount(userVideoAction.getCoinAmount());
                                                        return userVideoActionVO;
                                                    }).toList();
        }
    }

    /**
     * 更新redis缓存中的统计信息
     *
     * @param userId 用户ID
     */
    private void updateUserState(long userId)
    {
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        // 更新硬币数到缓存中
        // todo 保存关注数、粉丝数
        accountRedisRepository.saveUserState(userId, new UserState(0, 0, userInfo.getCurrentCoin()), expireDays);
    }
}
