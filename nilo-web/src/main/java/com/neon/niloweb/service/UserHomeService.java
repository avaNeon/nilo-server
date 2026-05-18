package com.neon.niloweb.service;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.UpdatedUserInfoDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userInfo.UserGender;
import com.neon.nilocommon.entity.enums.userInfo.UserTheme;
import com.neon.nilocommon.entity.enums.userVideoAction.VideoActionType;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.entity.vo.TokenUserInfoVO;
import com.neon.nilocommon.entity.vo.UserDetailVO;
import com.neon.nilocommon.entity.vo.VideoSeriesVideoVO;
import com.neon.nilocommon.entity.vo.userInfo.BriefUserInfoVO;
import com.neon.nilocommon.entity.vo.videoInfo.BriefVideoInfoVO;
import com.neon.nilocommon.entity.vo.videoSeriesInfo.VideoSeriesWithVideosVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.EnumFieldChecker;
import com.neon.nilocommon.util.FileUtil;
import com.neon.niloweb.config.SystemConfig;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.*;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserHomeService
{
    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final FollowInfoMapper <FollowInfo, FollowInfoQuery> followInfoMapper;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final UserVideoActionMapper <UserVideoAction, UserVideoActionQuery> userVideoActionMapper;

    private final VideoSeriesInfoMapper <VideoSeriesInfo, VideoSeriesInfoQuery> videoSeriesInfoMapper;

    private final AccountService accountService;

    private final AccountRedisRepository accountRedisRepository;

    private final WebConfig webConfig;

    private final SystemConfig systemConfig;

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
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        BeanUtils.copyProperties(userInfo, userDetailVO);

        Integer followingCount = followInfoMapper.selectFollowingCount(visitorUserId);
        Integer followerCount = followInfoMapper.selectFollowerCount(visitorUserId);
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

        // TODO 后续增加点赞数、播放数
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
        // 如果用户仅有缓存，在数据库层面被删除
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
        // 如果头像更改
        if (!dbUserInfo.getAvatar().equals(updatedUserInfoDTO.getAvatar()))
        {
            avatarChanged = true;
            Path tmpPath = Path.of(webConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME);
            if (!FileUtil.fileExists(tmpPath.toString(), updatedUserInfoDTO.getAvatar()))
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
        if (updatedUserInfoDTO.getBirthday() != null && (updatedUserInfoDTO.getBirthday()
                                                                           .isBlank() || !updatedUserInfoDTO.getBirthday()
                                                                                                            .matches(
                                                                                                                    "\\d{4}-\\d{2}-\\d{2}")))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 检测是否有足够硬币
        if (nickNameChanged)
        {
            Integer line = userInfoMapper.decreaseCoin(userId, systemConfig.getModifyNickNameCost());
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
                                                          updatedUserInfoDTO.getAvatar(),
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

            // 文件系统
            if (avatarChanged)
            {
                safelyMoveFile(updatedUserInfoDTO.getAvatar(), tokenUserInfoVO.getToken());
            }

            // 如果修改了昵称，会消耗硬币，所以还需要修改UserState
            if (nickNameChanged)
            {
                UserState userState = accountService.getUserStateByUserId(userId);
                // TODO 后续可能还有获赞数、播放数
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
     * @param userId 视频所属者用户ID
     * @param pageNo 页号
     * @return 视频分页列表
     */
    public PaginationResponseVO <BriefVideoInfoVO> loadVideo(long userId, int pageNo)
    {
        // 检查用户是否存在
        checkUserExists(userId);
        // 查询视频记录
        VideoInfoQuery videoInfoQuery = new VideoInfoQuery();
        videoInfoQuery.setUserId(userId);
        Integer totalCount = videoInfoMapper.selectCount(videoInfoQuery);
        int pageSize = webConfig.getPageSize();
        videoInfoQuery.setOrderBy("v.last_update_time DESC");
        videoInfoQuery.setPageCalculator(new PageCalculator(pageNo, totalCount, pageSize));
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
    public PaginationResponseVO <BriefVideoInfoVO> loadCollection(long userId, int pageNo)
    {
        // 检查用户是否存在
        checkUserExists(userId);

        // 查询收藏操作
        Integer totalCount = userVideoActionMapper.selectCountByUserIdAndActionType(userId, VideoActionType.SAVE.getValue());
        int pageSize = webConfig.getPageSize();
        UserVideoActionQuery query = new UserVideoActionQuery();
        query.setUserId(userId);
        query.setActionType(VideoActionType.SAVE.getValue());
        query.setPageCalculator(new PageCalculator(pageNo, totalCount, pageSize));
        query.setOrderBy("u.action_time DESC");
        List <UserVideoAction> userVideoActionList = userVideoActionMapper.selectList(query);

        // 转换为 video_id 列表
        List <Long> videoIdList = userVideoActionList.stream().map(UserVideoAction::getVideoId).toList();

        // 查询视频记录
        List <BriefVideoInfoVO> briefVideoInfoVOList = videoInfoMapper.selectBriefVoListByVideoIdBatch(videoIdList);
        return new PaginationResponseVO <>(totalCount, pageSize, pageNo, briefVideoInfoVOList);
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
        return videoSeriesInfoList.stream().map(videoSeriesInfo ->
                                                {
                                                    VideoSeriesWithVideosVO videoSeriesWithVideosVO = new VideoSeriesWithVideosVO();
                                                    BeanUtils.copyProperties(videoSeriesInfo, videoSeriesWithVideosVO);
                                                    videoSeriesWithVideosVO.setVideoInfoList(seriesIdMap.getOrDefault(
                                                            videoSeriesInfo.getSeriesId(),
                                                            List.of()));
                                                    return videoSeriesWithVideosVO;
                                                }).toList();
    }

    /**
     * 安全移动文件（出现异常时，帮助Redis回滚）
     *
     * @param fileRelativePathStr 需要安全移动的文件的相对路径
     * @param rollBackToken       回滚时需要删除的token
     */
    private void safelyMoveFile(String fileRelativePathStr, String rollBackToken)
    {
        try
        {
            FileUtil.verifyAndMoveCover(webConfig.getRootFilePath(), fileRelativePathStr);
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