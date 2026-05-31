package com.neon.niloweb.service;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoSearch.OrderType;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.vo.userInfo.BriefUserInfoVO;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoInfoDocListWithPagination;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoInfoDocVO;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoSearchResultVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.UserInfoMapper;
import com.neon.niloweb.repository.elasticsearch.VideoInfoDocRepository;
import com.neon.niloweb.repository.redis.CategoryRedisRepository;
import com.neon.niloweb.repository.redis.VideoSearchRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class VideoSearchService
{
    // ----- MySQL Mapper -----

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    // ----- Redis Repository -----

    private final VideoInfoDocRepository videoInfoDocRepository;

    private final CategoryRedisRepository categoryRedisRepository;

    private final VideoSearchRedisRepository videoSearchRedisRepository;

    // ----- config -----

    private final WebConfig webConfig;

    /**
     * 搜索视频
     *
     * @param orderType    排序方式
     * @param pageNo       页号
     * @param pageSize     页大小
     * @param useHighlight 是否开始高亮
     * @param keyword      搜索关键词
     * @return 视频信息列表
     */
    public VideoSearchResultVO searchVideo(Short orderType,
                                           Integer pageNo,
                                           Integer pageSize,
                                           Boolean useHighlight,
                                           String keyword)
    {
        String sortFieldName;

        // 校验并转化排序方式
        if (orderType == OrderType.NEWEST.getValue()) sortFieldName = "lastUpdateTime";
        else if (orderType == OrderType.MOST_PLAYED.getValue()) sortFieldName = "playCount";
        else if (orderType == OrderType.MOST_COLLECTED.getValue()) sortFieldName = "collectCount";
        else if (orderType == OrderType.MOST_DANMAKU.getValue()) sortFieldName = "danmakuCount";
        else
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 从ES中搜索数据
        VideoInfoDocListWithPagination videoInfoDocListWithPagination = videoInfoDocRepository.searchVideoInfo(keyword,
                                                                                                               pageNo,
                                                                                                               pageSize,
                                                                                                               useHighlight,
                                                                                                               sortFieldName);
        // 如果查询结果为空，直接返回
        if (videoInfoDocListWithPagination.getVideoInfoDocList() == null || videoInfoDocListWithPagination.getVideoInfoDocList()
                                                                                                          .isEmpty())
        {
            return new VideoSearchResultVO(videoInfoDocListWithPagination.getPageCalculator(), List.of());
        }

        // 获取分类缓存，用于分类 ID->number 转换
        List <CategoryInfo> categoryInfoList = categoryRedisRepository.getCategoryInfo();

        // 将其转化为用categoryId为key的map映射
        Map <Integer, CategoryInfo> categoryIdMap = categoryInfoList.stream()
                                                                    .collect(Collectors.toMap(CategoryInfo::getCategoryId,
                                                                                              categoryInfo -> categoryInfo));

        // 获取视频包含的所有用户信息
        List <UserInfo> userInfoList = userInfoMapper.selectBatchByUserId(videoInfoDocListWithPagination.getVideoInfoDocList()
                                                                                                        .stream()
                                                                                                        .map(VideoInfoDoc::getUserId)
                                                                                                        .distinct()
                                                                                                        .toList());
        // 将其转化为用userId为key的map映射
        Map <Long, UserInfo> userInfoMap = userInfoList.stream()
                                                       .collect(Collectors.toMap(UserInfo::getUserId, userInfo -> userInfo));

        // 开始赋值
        VideoSearchResultVO videoSearchResultVO = new VideoSearchResultVO();

        // 赋值分页信息
        videoSearchResultVO.setPageCalculator(videoInfoDocListWithPagination.getPageCalculator());

        // 将 videoInfoDoc 列表 转化为 videoInfoDocVO 列表
        List <VideoInfoDocVO> videoInfoDocVOList = videoInfoDocListWithPagination.getVideoInfoDocList()
                                                                                 .stream()
                                                                                 .map(videoInfoDoc ->
                                                                                      {
                                                                                          VideoInfoDocVO videoInfoDocVO = new VideoInfoDocVO();

                                                                                          // 先把视频信息都赋值
                                                                                          BeanUtils.copyProperties(videoInfoDoc,
                                                                                                                   videoInfoDocVO);

                                                                                          // 查出用户信息
                                                                                          UserInfo userInfo = userInfoMap.get(
                                                                                                  videoInfoDoc.getUserId());

                                                                                          // 将用户信息赋值
                                                                                          BriefUserInfoVO briefUserInfoVO = new BriefUserInfoVO();
                                                                                          BeanUtils.copyProperties(userInfo,
                                                                                                                   briefUserInfoVO);
                                                                                          videoInfoDocVO.setBriefUserInfoVO(
                                                                                                  briefUserInfoVO);

                                                                                          // 将分类信息赋值
                                                                                          CategoryInfo categoryInfo = categoryIdMap.get(
                                                                                                  videoInfoDoc.getCategoryId());
                                                                                          videoInfoDocVO.setCategoryNumber(
                                                                                                  categoryInfo.getCategoryNumber());

                                                                                          return videoInfoDocVO;
                                                                                      })
                                                                                 .toList();

        // 赋值视频信息
        videoSearchResultVO.setVideoInfoDocList(videoInfoDocVOList);

        // 记录搜索关键词，统计热搜榜单
        videoSearchRedisRepository.addHotKeywordCount(keyword);

        return videoSearchResultVO;
    }

    /**
     * 获取热搜记录
     *
     * @return 热搜记录
     */
    public List <String> getHotKeyword()
    {
        return videoSearchRedisRepository.getHotKeywordRanking(webConfig.getHotKeywordDisplayCount());
    }
}
