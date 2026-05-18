package com.neon.niloweb.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.po.VideoSeriesInfo;
import com.neon.nilocommon.entity.po.VideoSeriesVideo;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.query.VideoSeriesInfoQuery;
import com.neon.nilocommon.entity.query.VideoSeriesVideoQuery;
import com.neon.nilocommon.entity.vo.videoSeriesInfo.VideoSeriesInfoVO;
import com.neon.nilocommon.entity.vo.videoInfo.BasicVideoInfo;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.UserInfoMapper;
import com.neon.niloweb.mapper.VideoInfoMapper;
import com.neon.niloweb.mapper.VideoSeriesInfoMapper;
import com.neon.niloweb.mapper.VideoSeriesVideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Service
public class VideoSeriesService
{
    private final VideoSeriesInfoMapper <VideoSeriesInfo, VideoSeriesInfoQuery> videoSeriesInfoMapper;

    private final VideoSeriesVideoMapper <VideoSeriesVideo, VideoSeriesVideoQuery> videoSeriesVideoMapper;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final Snowflake snowflake;

    private final WebConfig webConfig;

    /**
     * 分页获取合集信息
     *
     * @param userId   用户ID
     * @param pageNo   页号
     * @param pageSize 页大小
     * @return 合集列表
     */
    public List <VideoSeriesInfoVO> loadVideoSeries(long userId, int pageNo, int pageSize)
    {
        // 校验用户是否存在
        checkUserExists(userId);

        int start = (pageNo - 1) * pageSize;
        return videoSeriesInfoMapper.selectVideoSeriesInfoVoWithCoverByUserId(userId, start, pageSize);
    }

    /**
     * 将合集重新排序
     *
     * @param userId       用户ID
     * @param seriesIdList 合集列表
     */
    public void resortVideoSeries(long userId, List <Long> seriesIdList)
    {
        // 校验合集是否都存在并属于用户 & 去重
        seriesIdList = checkSeriesListOwner(userId, seriesIdList);

        // 入库
        AtomicInteger no = new AtomicInteger(1);
        videoSeriesInfoMapper.resort(userId,
                                     seriesIdList.stream()
                                                 .map(seriesid -> new VideoSeriesInfo(seriesid,
                                                                                      null,
                                                                                      null,
                                                                                      null,
                                                                                      no.getAndIncrement(),
                                                                                      null))
                                                 .toList());
    }

    /**
     * 新增视频合集
     *
     * @param userId            用户ID
     * @param seriesName        合集名称
     * @param seriesDescription 合集简介
     */
    @Transactional(rollbackFor = Exception.class)
    public void addVideoSeries(long userId, String seriesName, String seriesDescription, List <Long> videoIdList)
    {
        // 校验 videoIdList & 去重
        videoIdList = checkVideoListOwner(userId, videoIdList);

        // 准备 series_id
        long seriesId = snowflake.nextId();

        // 准备 sort_index（排在最后）
        Integer sortIndex = videoSeriesInfoMapper.selectMaxSortIndexByUserId(userId) + 1;

        // 入库
        // VideoSeriesInfo
        videoSeriesInfoMapper.insert(new VideoSeriesInfo(seriesId,
                                                         userId,
                                                         seriesName,
                                                         seriesDescription,
                                                         sortIndex,
                                                         LocalDateTime.now()));
        // VideoSeriesVideo
        AtomicInteger no = new AtomicInteger(1);
        List <VideoSeriesVideo> videoSeriesVideoList = videoIdList.stream()
                                                                  .map(videoId -> new VideoSeriesVideo(seriesId,
                                                                                                       videoId,
                                                                                                       userId,
                                                                                                       no.getAndIncrement()))
                                                                  .toList();
        videoSeriesVideoMapper.insertBatch(videoSeriesVideoList);
    }

    /**
     * 修改视频合集
     *
     * @param userId            用户ID
     * @param seriesId          合集ID
     * @param seriesName        合集名称
     * @param seriesDescription 合集简介
     * @param videoIdList       视频ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateVideoSeries(long userId,
                                  long seriesId,
                                  String seriesName,
                                  String seriesDescription,
                                  List <Long> videoIdList)
    {
        // 校验 videoIdList & 去重
        videoIdList = checkVideoListOwner(userId, videoIdList);

        // 校验seriesId是否存在
        VideoSeriesInfo videoSeriesInfo = videoSeriesInfoMapper.selectBySeriesId(seriesId);
        if (videoSeriesInfo == null)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 准备 sort_index（排在最后）
        Integer sortIndex = videoSeriesInfoMapper.selectMaxSortIndexByUserId(userId) + 1;

        // 入库
        // VideoSeriesInfo
        videoSeriesInfoMapper.updateBySeriesId(new VideoSeriesInfo(null,
                                                                   userId,
                                                                   seriesName,
                                                                   seriesDescription,
                                                                   sortIndex,
                                                                   LocalDateTime.now()), seriesId);
        // VideoSeriesVideo
        AtomicInteger no = new AtomicInteger(1);
        List <VideoSeriesVideo> videoSeriesVideoList = videoIdList.stream()
                                                                  .map(videoId -> new VideoSeriesVideo(seriesId,
                                                                                                       videoId,
                                                                                                       userId,
                                                                                                       no.getAndIncrement()))
                                                                  .toList();
        videoSeriesVideoMapper.resort(seriesId, videoSeriesVideoList);
    }

    /**
     * 向合集中添加一个视频
     *
     * @param userId   用户ID
     * @param seriesId 集合ID
     * @param videoId  视频ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void insertVideoToSeries(long userId, long seriesId, long videoId)
    {
        // 校验seriesId
        checkSeriesOwner(userId, seriesId);
        // 校验videoId
        checkVideoOwner(userId, videoId);
        // 校验视频是否已经在合集中
        if (checkVideoExistsInSeries(seriesId, videoId))
        {
            throw new BusinessException(ResponseCode.DATA_EXISTED);
        }

        // 入库
        // video_series_info
        // 写入最新的更新时间
        videoSeriesInfoMapper.updateBySeriesId(new VideoSeriesInfo(null, null, null, null, null, LocalDateTime.now()), seriesId);
        // video_series_video
        Integer sortIndex = videoSeriesVideoMapper.selectMaxSortIndex(seriesId) + 1;
        videoSeriesVideoMapper.insert(new VideoSeriesVideo(seriesId, videoId, userId, sortIndex));
    }

    /**
     * 统计合集中没有的视频数量
     *
     * @param userId   用户ID
     * @param seriesId 合集ID
     * @return 视频数量
     */
    public Integer getVideoExcludingSeriesCount(long userId, long seriesId)
    {
        // 检查合集是否属于用户
        checkSeriesOwner(userId, seriesId);

        return videoInfoMapper.selectCountVideoInfoByUserIdExcludingSeriesId(userId, seriesId);
    }

    /**
     * 分页加载更多合集中没有的视频
     *
     * @param userId   用户ID
     * @param seriesId 合集ID
     * @param pageNo   页号
     * @return 合集中没有的视频
     */
    public List <BasicVideoInfo> loadMoreVideoExcludingSeries(long userId, long seriesId, int pageNo)
    {
        // 检查合集是否属于用户
        checkSeriesOwner(userId, seriesId);

        int pageSize = webConfig.getPageSize();
        int start = (pageNo - 1) * pageSize;
        List <VideoInfo> videoInfoList = videoInfoMapper.selectVideoInfoByUserIdExcludingSeriesId(userId,
                                                                                                  seriesId,
                                                                                                  start,
                                                                                                  pageSize);
        return videoInfoList.stream().map(videoInfo ->
                                          {
                                              BasicVideoInfo basicVideoInfo = new BasicVideoInfo();
                                              BeanUtils.copyProperties(videoInfo, basicVideoInfo);
                                              return basicVideoInfo;
                                          }).toList();
    }

    /**
     * 分页加载合集中的视频
     *
     * @param seriesId 合集ID
     * @param pageNo   页号
     * @return 合集中没有的视频
     */
    public List <BasicVideoInfo> loadSeriesVideo(long seriesId, int pageNo)
    {
        // 检查合集是否存在
        checkSeriesExists(seriesId);

        // 构建查询条件
        int pageSize = webConfig.getPageSize();
        int start = (pageNo - 1) * pageSize;

        // 查询
        List <VideoInfo> videoInfoList = videoInfoMapper.selectVideoInfoBySeriesId(seriesId, start, pageSize);
        return videoInfoList.stream().map(videoInfo ->
                                          {
                                              BasicVideoInfo basicVideoInfo = new BasicVideoInfo();
                                              BeanUtils.copyProperties(videoInfo, basicVideoInfo);
                                              return basicVideoInfo;
                                          }).toList();
    }

    /**
     * 从合集中移除一个视频
     *
     * @param userId   用户ID
     * @param seriesId 合集ID
     * @param videoId  视频ID
     */
    public void deleteSeriesVideo(long userId, long seriesId, long videoId)
    {
        // 校验集合是否属于用户
        checkSeriesOwner(userId, seriesId);
        // 校验视频是否在集合中
        if (!checkVideoExistsInSeries(seriesId, videoId))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        // 校验视频是否属于用户
        checkVideoOwner(userId, videoId);

        videoSeriesVideoMapper.deleteBySeriesIdAndVideoId(seriesId, videoId);
    }

    /**
     * 删除合集
     *
     * @param userId   用户ID
     * @param seriesId 合集ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSeries(long userId, long seriesId)
    {
        // 校验集合是否属于用户
        checkSeriesOwner(userId, seriesId);

        // 删除videoSeriesInfo
        videoSeriesInfoMapper.deleteByUserIdAndSeriesId(userId, seriesId);
        // 删除对应seriesId下所有视频记录
        videoSeriesVideoMapper.deleteByUserIdAndSeriesId(userId, seriesId);
    }

    /**
     * 校验用户是否存在于MySQL记录中
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

    /**
     * 校验合集是否存在于MySQL记录中
     *
     * @param seriesId 合集ID
     */
    private void checkSeriesExists(long seriesId)
    {
        VideoSeriesInfo videoSeriesInfo = videoSeriesInfoMapper.selectBySeriesId(seriesId);
        if (videoSeriesInfo == null)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
    }

    /**
     * 校验视频合集是否存在并属于指定用户
     *
     * @param userId   用户ID
     * @param seriesId 合集ID
     */
    private void checkSeriesOwner(long userId, long seriesId)
    {
        VideoSeriesInfo videoSeriesInfo = videoSeriesInfoMapper.selectBySeriesId(seriesId);
        if (videoSeriesInfo == null || videoSeriesInfo.getUserId() != userId)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
    }

    /**
     * 校验视频是否存在并属于指定用户
     *
     * @param userId  用户ID
     * @param videoId 视频ID
     */
    private void checkVideoOwner(long userId, long videoId)
    {
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        if (videoInfo == null || videoInfo.getUserId() != userId)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
    }

    /**
     * 查看视频是否存在于合集中
     *
     * @param seriesId 合集ID
     * @param videoId  视频ID
     * @return 若存在，返回 true ，否则返回 false
     */
    private boolean checkVideoExistsInSeries(long seriesId, long videoId)
    {
        VideoSeriesVideo videoSeriesVideo = videoSeriesVideoMapper.selectBySeriesIdAndVideoId(seriesId, videoId);
        return videoSeriesVideo != null;
    }

    /**
     * 检查合集列表是否完整并且均属于指定用户
     *
     * @param userId       用户ID
     * @param seriesIdList 合集ID列表
     * @return 去重后的合集ID列表
     */
    private List <Long> checkSeriesListOwner(long userId, List <Long> seriesIdList)
    {
        // seriesId 不能含 null 值
        if (seriesIdList.stream().anyMatch(Objects::isNull))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 先去重
        List <Long> distinctSeriesIdList = seriesIdList.stream().distinct().toList();

        // 检查是否每条记录都存在（seriesId是主键，不可能出现两条seriesId都一样的记录）
        Integer count = videoSeriesInfoMapper.selectCountByUserIdAndSeriesIdList(userId, distinctSeriesIdList);
        if (count == null || count != distinctSeriesIdList.size())
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        return distinctSeriesIdList;
    }

    /**
     * 检查视频列表是否完整并且均属于指定用户
     *
     * @param userId      用户ID
     * @param videoIdList 视频ID列表
     * @return 去重后的视频ID列表
     */
    private List <Long> checkVideoListOwner(long userId, List <Long> videoIdList)
    {
        // videoId 不能含 null 值
        if (videoIdList.stream().anyMatch(Objects::isNull))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 先去重
        List <Long> distinctVideoIdList = videoIdList.stream().distinct().toList();

        // 检查是否每条记录都存在（videoId是主键，不可能出现两条videoId都一样的记录）
        Integer count = videoInfoMapper.selectCountByUserIdAndVideoIdList(userId, distinctVideoIdList);
        if (count == null || count != distinctVideoIdList.size())
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        return distinctVideoIdList;
    }
}
