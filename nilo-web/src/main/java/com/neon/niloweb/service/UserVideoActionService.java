package com.neon.niloweb.service;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userVideoAction.VideoActionType;
import com.neon.nilocommon.entity.po.UserInfo;
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
import com.neon.niloweb.repository.elasticsearch.VideoInfoDocRepository;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserVideoActionService
{
    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final UserVideoActionMapper <UserVideoAction, UserVideoActionQuery> userVideoActionMapper;

    private final AccountRedisRepository accountRedisRepository;

    private final VideoInfoDocRepository videoInfoDocRepository;

    private final UserMessageService userMessageService;

    /**
     * 视频操作记录
     *
     * @param userId     用户ID
     * @param videoId    视频ID
     * @param actionType 操作类型
     * @param coinAmount 如果是0，代表不是投币操作
     */
    @Transactional(rollbackFor = Exception.class)
    public void videoAction(long userId, long videoId, short actionType, short coinAmount)
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
        Optional <VideoActionType> actionTypeEnumOptional = EnumFieldChecker.findByFieldValue(VideoActionType.class,
                                                                                              "value",
                                                                                              actionType);
        if (Objects.isNull(actionTypeEnumOptional) || actionTypeEnumOptional.isEmpty())
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        VideoActionType actionTypeEnum = actionTypeEnumOptional.get();

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

            switch (actionTypeEnum)
            {
                // LIKE
                case LIKE ->
                {
                    // 更新视频点赞数
                    videoInfoMapper.increaseLikeCount(videoId);

                    // 向用户异步发送消息
                    CompletableFuture <Void> likeMessageCompletableFuture = userMessageService.recordVideoActionMessage(videoInfo,
                                                                                                                        userId,
                                                                                                                        VideoActionType.LIKE);

                    CompletableFuture.allOf(likeMessageCompletableFuture).exceptionally(e ->
                                                                                        {
                                                                                            log.warn("异步发送给视频点赞消息时产生异常：{}",
                                                                                                     e.toString());
                                                                                            return null;
                                                                                        });
                }
                // COLLECT
                // todo添加到收藏夹操作
                case COLLECT ->
                {
                    /* 更新mysql */
                    // 更新收藏数
                    videoInfoMapper.increaseCollectCount(videoId);

                    /* 更新ES */
                    videoInfoDocRepository.increaseCollectCountByVideoId(videoId, 1);

                    // 向用户异步发送消息
                    CompletableFuture <Void> collectMessageCompletableFuture = userMessageService.recordVideoActionMessage(
                            videoInfo,
                            userId,
                            VideoActionType.COLLECT);

                    CompletableFuture.allOf(collectMessageCompletableFuture).exceptionally(e ->
                                                                                           {
                                                                                               log.warn("异步发送给视频收藏消息时产生异常：{}",
                                                                                                        e.toString());
                                                                                               return null;
                                                                                           });
                }
                // COIN
                case COIN ->
                {
                    videoInfoMapper.increaseCoinCount(videoId, coinAmount);
                    Integer updatedLine = userInfoMapper.decreaseCoin(userId, coinAmount);
                    if (updatedLine == 0)
                    {
                        throw new BusinessException("硬币余额不足");
                    }
                    // 更新mysql
                    userInfoMapper.increaseCoin(videoInfo.getUserId(), coinAmount);
                    // 删除redis中用户统计缓存信息
                    accountRedisRepository.deleteUserStateBatch(List.of(userId, videoInfo.getUserId()));
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

                    // 删除用户统计缓存信息
                    accountRedisRepository.deleteUserStateBatch(List.of(userId, videoInfo.getUserId()));
                }
            }
            // 点赞、收藏
            else
            {
                // 点赞、收藏过了，就删除

                // 更新user_video_action
                userVideoActionMapper.deleteByActionId(dbUserVideoAction.getActionId());

                // 更新video_info
                switch (actionType)
                {
                    // CANCEL THE LIKE
                    case 1 -> videoInfoMapper.decreaseLikeCount(videoId);
                    // CANCEL THE COLLECT
                    // todo 添加到收藏夹
                    case 2 ->
                    {
                        // 更新mysql
                        videoInfoMapper.decreaseCollectCount(videoId);
                        // 更新ES
                        videoInfoDocRepository.decreaseCollectCountByVideoId(videoId, 1);
                    }
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
}
