package com.neon.niloweb.service;

import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.dto.UpdatedUserInfoDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userInfo.UserGender;
import com.neon.nilocommon.entity.enums.userInfo.UserTheme;
import com.neon.nilocommon.entity.enums.userVideoAction.VideoActionType;
import com.neon.nilocommon.entity.enums.videoInfo.SortType;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.entity.tmp.VideoSeriesVideoCountTMP;
import com.neon.nilocommon.entity.vo.*;
import com.neon.nilocommon.entity.vo.userInfo.BriefUserInfoVO;
import com.neon.nilocommon.entity.vo.videoInfo.BriefVideoInfoVO;
import com.neon.nilocommon.entity.vo.videoInfo.CollectedVideoInfoVO;
import com.neon.nilocommon.entity.vo.videoSeriesInfo.VideoSeriesWithVideosVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import com.neon.nilocommon.util.EnumFieldChecker;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.feign.storage.ImageFeignClient;
import com.neon.niloweb.mapper.*;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserHomeService
{
    private final AccountService accountService;

    private final MediaOwnershipMapper <MediaOwnership, MediaOwnershipQuery> mediaOwnershipMapper;

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final FollowInfoMapper <FollowInfo, FollowInfoQuery> followInfoMapper;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoCommentMapper <VideoComment, VideoCommentQuery> videoCommentMapper;

    private final UserVideoActionMapper <UserVideoAction, UserVideoActionQuery> userVideoActionMapper;

    private final VideoSeriesInfoMapper <VideoSeriesInfo, VideoSeriesInfoQuery> videoSeriesInfoMapper;

    private final AccountRedisRepository accountRedisRepository;

    private final SystemConfigRedisRepository systemConfigRedisRepository;

    private final WebConfig webConfig;
    private final ImageFeignClient imageFeignClient;

    /**
     * 获取用户主页信息
     *
     * @param visitorUserId 访客用户ID
     * @param hostUserId    受访者用户ID
     */
    public UserDetailVO getUserDetail(Long visitorUserId, long hostUserId)
    {
        UserDetailVO userDetailVO = new UserDetailVO();
        UserInfo userInfo = userInfoMapper.selectByUserId(hostUserId);
        if (userInfo == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        BeanUtils.copyProperties(userInfo, userDetailVO);

        Integer followingCount = followInfoMapper.selectFollowingCount(hostUserId);
        Integer followerCount = followInfoMapper.selectFollowerCount(hostUserId);
        if (followingCount == null || followerCount == null)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        userDetailVO.setFollowingCount(followingCount);
        userDetailVO.setFollowerCount(followerCount);

        userDetailVO.setHasFollowed(false);
        if (visitorUserId != null && visitorUserId != hostUserId)
        {
            FollowInfo followInfo = followInfoMapper.selectByFollowerUserIdAndFollowingUserId(visitorUserId, hostUserId);
            userDetailVO.setHasFollowed(followInfo != null);
        }

        Long videoLikeCount = videoInfoMapper.selectLikeCountByUserId(hostUserId);
        Long commentLikeCount = videoCommentMapper.selectUpvoteCountByUserId(hostUserId);
        Long playCount = videoInfoMapper.selectPlayCountByUserId(hostUserId);
        userDetailVO.setLikeCount(videoLikeCount + commentLikeCount);
        userDetailVO.setPlayCount(playCount);

        return userDetailVO;
    }

    /**
     * 更新用户信息
     *
     * @param loginState         登录信息
     * @param updatedUserInfoDTO 更新后的用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public TokenUserInfoVO updateUserInfo(TokenUserInfo loginState, UpdatedUserInfoDTO updatedUserInfoDTO)
    {
        long userId = loginState.getUserInfo().getUserId();

        UserInfo dbUserInfo = userInfoMapper.selectByUserId(userId);
        // 如果用户仅有缓存，在数据库层面被删除，说明用户账号可能被注销了，不提供服务
        if (dbUserInfo == null || !dbUserInfo.getUserId().equals(userId))
        {
            throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
        }

        // ----- 校验 -----

        // 校验昵称是否修改
        boolean nickNameChanged = false;
        if (!updatedUserInfoDTO.getNickName().equals(dbUserInfo.getNickName()))
        {
            nickNameChanged = true;
            UserInfo userInfo = userInfoMapper.selectByNickName(updatedUserInfoDTO.getNickName());
            // !! 如果昵称已被占用，返回DATA_EXISTED !!
            if (userInfo != null)
            {
                throw new BusinessException(ResponseCode.DATA_EXISTED);
            }
        }

        // 校验头像图片是否存在
        boolean avatarChanged = false;
        String oldAvatar = dbUserInfo.getAvatar();
        String newAvatar = updatedUserInfoDTO.getAvatar();
        // 如果头像更改
        if (!Objects.equals(oldAvatar, newAvatar))
        {
            avatarChanged = true;

            // 校验图片归属（原图+缩略图强绑定校验）
            String newAvatarThumbnail = FileUtil.constructThumbnailName(newAvatar);
            List <String> newAvatarKeys = List.of(newAvatar, newAvatarThumbnail);
            int count = mediaOwnershipMapper.selectCountByObjectKeysAndOwnerIdAndUsed(newAvatarKeys, userId, 0);
            if (count != newAvatarKeys.size())
            {
                throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
            }
        }

        // 校验性别是否合法
        if (updatedUserInfoDTO.getGender() != null && !EnumFieldChecker.containsFieldValue(UserGender.class,
                                                                                           "gender",
                                                                                           updatedUserInfoDTO.getGender()))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        // 校验生日
        // 生日可以是空，但是只要有值就必须符合格式
        if (updatedUserInfoDTO.getBirthday() != null && (!updatedUserInfoDTO.getBirthday()
                                                                            .isBlank() && !updatedUserInfoDTO.getBirthday()
                                                                                                             .matches(
                                                                                                                     "\\d{4}-\\d{2}-\\d{2}")))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 检测是否有足够硬币
        if (nickNameChanged)
        {
            Integer line = userInfoMapper.decreaseCoin(userId,
                                                       systemConfigRedisRepository.getSystemConfig().getModifyNickNameCost());
            if (line < 1)
            {
                throw new BusinessException(ResponseCode.INSUFFICIENT_COIN);
            }
        }

        // ----- 数据更新 -----

        // MySQL
        UserInfo userInfo = new UserInfo();
        BeanUtils.copyProperties(updatedUserInfoDTO, userInfo);
        userInfoMapper.updateByUserId(userInfo, userId);

        // Redis
        // 如果头像或昵称变化，我们必须重新生成token，并更新redis中的数据，最后把新的token交给用户
        if (nickNameChanged || avatarChanged)
        {
            // 更新 TokenUserInfo
            TokenUserInfo tokenUserInfo = new TokenUserInfo();
            tokenUserInfo.setUserInfo(new BriefUserInfoVO(userId,
                                                          updatedUserInfoDTO.getNickName(),
                                                          newAvatar,
                                                          updatedUserInfoDTO.getPersonalIntroduction()));
            accountService.generateAndSaveToken(tokenUserInfo, webConfig.getUserInfoExpireDays());

            TokenUserInfoVO tokenUserInfoVO = new TokenUserInfoVO();
            BeanUtils.copyProperties(tokenUserInfo, tokenUserInfoVO);

            // 先取出来备用，以防异常影响流程
            UserInfo modifiedUserInfo = null;
            if (nickNameChanged)
            {
                modifiedUserInfo = userInfoMapper.selectByUserId(userId);
                if (modifiedUserInfo == null)
                {
                    throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
                }
            }

            // 移动新头像，删除旧头像
            if (avatarChanged)
            {
                String newAvatarThumbnail = FileUtil.constructThumbnailName(newAvatar);
                List <String> newAvatarKeys = List.of(newAvatar, newAvatarThumbnail);

                // 启动新头像及缩略图
                // 执行后要检查下实际更改行数，否则可能出现TOCTOU（就是用户在上次检查和这次更改之间启用文件）
                int affected = mediaOwnershipMapper.markAsUsedBatch(newAvatarKeys, userId, LocalDateTime.now());
                if (affected != newAvatarKeys.size())
                {
                    throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
                }

                // 删除旧头像及缩略图记录
                if (oldAvatar != null && !oldAvatar.isBlank())
                {
                    String oldAvatarThumbnail = FileUtil.constructThumbnailName(oldAvatar);
                    mediaOwnershipMapper.deleteByObjectKey(oldAvatar);
                    mediaOwnershipMapper.deleteByObjectKey(oldAvatarThumbnail);
                }

                // 这一步如果成功，就确定了用新的头像替代旧头像
                safelyMoveAvatarKey(newAvatar, tokenUserInfoVO.getToken());
                safelyMoveAvatarKey(newAvatarThumbnail, tokenUserInfoVO.getToken());

                // 接下来就不能回滚了（只能在失败时打日志了）
                if (oldAvatar != null && !oldAvatar.isBlank())
                {
                    String oldAvatarThumbnail = FileUtil.constructThumbnailName(oldAvatar);
                    ResponseVO <Void> oldAvatarDeleted = imageFeignClient.delete(oldAvatar);
                    if (oldAvatarDeleted == null || !oldAvatarDeleted.getStatus().equals(ResponseVO.STATUS_SUCCESS))
                    {
                        log.error("删除旧头像{}失败！", oldAvatar);
                    }
                    ResponseVO <Void> oldAvatarThumbnailDeleted = imageFeignClient.delete(oldAvatarThumbnail);
                    if (oldAvatarThumbnailDeleted == null || !oldAvatarThumbnailDeleted.getStatus().equals(ResponseVO.STATUS_SUCCESS))
                    {
                        log.error("删除旧头像缩略图{}失败！", oldAvatarThumbnail);
                    }
                }
            }

            // 如果修改了昵称，会消耗硬币，所以还需要修改UserState
            if (nickNameChanged)
            {
                UserState userState = accountService.getUserStateByUserId(userId);
                userState.setCurrentCoin(modifiedUserInfo.getCurrentCoin());
                try
                {
                    accountRedisRepository.saveUserState(userId, userState, webConfig.getUserInfoExpireDays());
                }
                catch (Exception e) // 要是这步出错了就把UserState删除，下次让用户自己去库里刷新
                {
                    try
                    {
                        accountRedisRepository.deleteUserState(userId);
                    }
                    catch (Exception innerException)
                    {
                        // 算你狠
                        log.error("删除redis中用户名为{}的 user state 记录失败！", userId);
                    }
                }
                // 回写
                BeanUtils.copyProperties(userState, tokenUserInfoVO);
            }
            else
            {
                // 回写
                UserState userState = accountService.getUserStateByUserId(userId);
                BeanUtils.copyProperties(userState, tokenUserInfoVO);
            }

            // 删除旧 TokenUserInfo Key
            try
            {
                accountRedisRepository.deleteTokenUserInfo(loginState.getToken());
            }
            catch (Exception e)
            {
                log.warn("删除旧用户登录token失败！用户ID={},异常信息：{}", userId, e.toString());
            }

            return tokenUserInfoVO;
        }

        return null;
    }

    /**
     * 设置个人主页主题
     *
     * @param userId     用户ID
     * @param themeIndex 主题标号
     */
    public void saveTheme(long userId, short themeIndex)
    {
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        // 可能是用户已经被删除，缓存还留着记录
        if (userInfo == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }
        // 校验参数是否符合枚举中定义的数值
        if (!EnumFieldChecker.containsFieldValue(UserTheme.class, "value", themeIndex))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        UserInfo newUserInfo = new UserInfo();
        newUserInfo.setTheme(themeIndex);
        userInfoMapper.updateByUserId(newUserInfo, userId);
    }

    /**
     * 查询指定用户发布视频列表<hr/>
     * 视频分页大小由配置文件固定
     *
     * @param userId     视频所属者用户ID
     * @param pageNo     页号
     * @param pageSize
     * @param sortTypeNo
     * @param keyword
     * @return 视频分页列表
     */
    public PaginationResponseVO <BriefVideoInfoVO> loadVideo(long userId,
                                                             int pageNo,
                                                             int pageSize,
                                                             short sortTypeNo,
                                                             String keyword)
    {
        // 检查用户是否存在
        checkUserExists(userId);
        // 查询视频记录
        VideoInfoQuery videoInfoQuery = new VideoInfoQuery();
        videoInfoQuery.setUserId(userId);
        if (keyword != null && !keyword.isEmpty())
        {
            videoInfoQuery.setVideoNameFuzzy(keyword);
        }
        Integer totalCount = videoInfoMapper.selectCount(videoInfoQuery);

        // 校验排序类型
        Optional <SortType> sortType = EnumFieldChecker.findByFieldValue(SortType.class, "no", sortTypeNo);
        if (sortType.isEmpty())
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        else
        {
            videoInfoQuery.setOrderBy(sortType.get().getValue());
        }


        videoInfoQuery.setPageCalculator(new PageCalculator(pageNo, totalCount, pageSize));

        if ((pageNo - 1) * pageSize >= totalCount)
        {
            return new PaginationResponseVO <>(totalCount, pageSize, pageNo, List.of());
        }

        List <BriefVideoInfoVO> briefVideoInfoVOList = videoInfoMapper.selectBriefVoListByParam(videoInfoQuery);

        return new PaginationResponseVO <>(totalCount, pageSize, pageNo, briefVideoInfoVOList);
    }

    /**
     * 查询指定用户收藏视频列表<hr/>
     * 视频分页大小由配置文件固定
     *
     * @param userId 视频收藏者用户ID
     * @param pageNo 页号
     * @return 收藏视频分页列表
     */
    public PaginationResponseVO <CollectedVideoInfoVO> loadCollection(long userId, int pageNo)
    {
        // 检查用户是否存在
        checkUserExists(userId);

        // 查询收藏操作
        Integer totalCount = userVideoActionMapper.selectCountByUserIdAndActionType(userId, VideoActionType.COLLECT.getValue());
        int pageSize = webConfig.getPageSize();

        // 如果没数据，直接返回
        if (totalCount == 0)
        {
            return new PaginationResponseVO <>(totalCount, pageSize, pageNo, List.of());
        }

        UserVideoActionQuery query = new UserVideoActionQuery();
        query.setUserId(userId);
        query.setActionType(VideoActionType.COLLECT.getValue());
        query.setPageCalculator(new PageCalculator(pageNo, totalCount, pageSize));
        query.setOrderBy("u.action_time DESC");
        List <UserVideoAction> userVideoActionList = userVideoActionMapper.selectList(query);

        if (userVideoActionList == null || userVideoActionList.isEmpty())
        {
            return new PaginationResponseVO <>(totalCount, pageSize, pageNo, List.of());
        }
        else
        {

            // 转换为 video_id 列表
            List <Long> videoIdList = userVideoActionList.stream().map(UserVideoAction::getVideoId).toList();

            // 查询视频记录
            List <BriefVideoInfoVO> briefVideoInfoVOList = videoInfoMapper.selectBriefVoListByVideoIdBatch(videoIdList);

            // 将 userVideoActionList 转化为 id 和 value 的映射
            Map <Long, UserVideoAction> idActionMap = userVideoActionList.stream()
                                                                         .collect(Collectors.toMap(UserVideoAction::getVideoId,
                                                                                                   userVideoAction -> userVideoAction));

            List <CollectedVideoInfoVO> voList = briefVideoInfoVOList.stream().map(item ->
                                                                                   {
                                                                                       UserVideoAction videoAction = idActionMap.get(
                                                                                               item.getVideoId());
                                                                                       CollectedVideoInfoVO vo = new CollectedVideoInfoVO();
                                                                                       BeanUtils.copyProperties(item, vo);
                                                                                       vo.setCollectDate(videoAction.getActionTime());
                                                                                       return vo;
                                                                                   }).toList();

            return new PaginationResponseVO <>(totalCount, pageSize, pageNo, voList);
        }
    }

    /**
     * 获取用户主页合集信息
     *
     * @param userId 用户ID
     * @return 合集列表（包括部分视频）
     */
    public List <VideoSeriesWithVideosVO> loadSeriesWithVideos(long userId)
    {
        // 校验用户是否存在
        checkUserExists(userId);

        // 先查询出series
        List <VideoSeriesInfo> videoSeriesInfoList = videoSeriesInfoMapper.selectVideoSeriesListByUserId(userId,
                                                                                                         webConfig.getUserHomeSeriesDisplaySize());
        // 如果列表为空，说明用户还没有任何合集，返回空列表
        if (videoSeriesInfoList == null || videoSeriesInfoList.isEmpty())
        {
            return new ArrayList <>();
        }

        // 转化成seriesId列表
        List <Long> seriesIdList = videoSeriesInfoList.stream().map(VideoSeriesInfo::getSeriesId).toList();
        // 查询所有需要的videoSeriesVideoVO
        List <VideoSeriesVideoVO> videoInfoList = videoInfoMapper.selectVideoInfoBySeriesIdBatch(seriesIdList,
                                                                                                 webConfig.getUserHomeSeriesVideoDisplaySize());
        Map <Long, List <VideoSeriesVideoVO>> seriesIdMap = videoInfoList.stream()
                                                                         .collect(Collectors.groupingBy(VideoSeriesVideoVO::getSeriesId));

        // 查询系列中的视频数量，并回填
        List <VideoSeriesVideoCountTMP> videoCountList = videoInfoMapper.selectVideoCountBySeriesIdBatch(seriesIdList);
        Map <Long, Integer> seriesVideoCountMap = videoCountList.stream()
                                                                .collect(Collectors.toMap(VideoSeriesVideoCountTMP::getSeriesId,
                                                                                          VideoSeriesVideoCountTMP::getVideoCount));
        return videoSeriesInfoList.stream()
                                  .map(videoSeriesInfo ->
                                       {
                                           VideoSeriesWithVideosVO videoSeriesWithVideosVO = new VideoSeriesWithVideosVO();
                                           BeanUtils.copyProperties(videoSeriesInfo, videoSeriesWithVideosVO);
                                           videoSeriesWithVideosVO.setVideoCount(seriesVideoCountMap.getOrDefault(videoSeriesInfo.getSeriesId(),
                                                                                                                  0));
                                           videoSeriesWithVideosVO.setVideoInfoList(seriesIdMap.getOrDefault(videoSeriesInfo.getSeriesId(),
                                                                                                             List.of()));
                                           return videoSeriesWithVideosVO;
                                       })
                                  .filter(videoSeriesWithVideosVO -> videoSeriesWithVideosVO.getVideoCount() > 0) // 没有视频的系列不展示
                                  .toList();
    }

    /**
     * 安全移动头像key（出现异常时，帮助Redis回滚）
     *
     * @param key           需要安全移动的key（不含前缀）
     * @param rollBackToken 回滚时需要删除的token
     */
    private void safelyMoveAvatarKey(String key, String rollBackToken)
    {
        try
        {
            // 这里的移动操作是幂等的，如果已经移动到目标位置了，再次移动不会有任何影响
            imageFeignClient.move(MinioKey.TMP_PREFIX + key, MinioKey.PUBLIC_PREFIX + key);
        }
        catch (Exception e)
        {
            accountRedisRepository.deleteTokenUserInfo(rollBackToken);
            throw e;
        }
    }

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
}
