package com.neon.niloweb.service;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoInfo.RecommendType;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.entity.vo.VideoInfoFileVO;
import com.neon.nilocommon.entity.vo.videoInfo.BriefVideoInfoVO;
import com.neon.nilocommon.entity.vo.videoInfo.VideoInfoVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.*;
import com.neon.niloweb.repository.redis.CategoryRedisRepository;
import com.neon.niloweb.repository.redis.HotVideoRedisRepository;
import com.neon.niloweb.service.async.VideoAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static com.neon.nilocommon.entity.constants.RedisKey.CATEGORY_UPDATE_LOCK;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoService
{
    private final VideoAsyncService videoAsyncService;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final CategoryInfoMapper <CategoryInfo, CategoryInfoQuery> categoryInfoMapper;

    private final FollowInfoMapper <FollowInfo, FollowInfoQuery> followInfoMapper;

    private final CategoryRedisRepository categoryRedisRepository;

    private final HotVideoRedisRepository hotVideoRedisRepository;

    private final WebConfig webConfig;

    private final RedissonClient redisson;

    /**
     * 本地播放数统计缓存
     */
    private final Queue <Long> playCountBuffer = new ConcurrentLinkedQueue <>();

    /**
     * 本地缓存一次批量发送播放消息条数
     */
    private final Integer batchSize = 2000;

    /**
     * 获取所有推荐视频<hr/>
     * <ul>
     *     <li>时间顺序从新到旧</li>
     * </ul>
     *
     * @return 推荐视频列表
     */
    public List <BriefVideoInfoVO> loadRecommendVideo()
    {
        VideoInfoQuery infoQuery = new VideoInfoQuery();
        infoQuery.setOrderBy("create_time desc");
        infoQuery.setRecommendType(RecommendType.RECOMMENDED.getType());
        List <BriefVideoInfoVO> list = videoInfoMapper.selectBriefVoListByParam(infoQuery);
        fillCategoryNumbers(list);
        return list;
    }

    /**
     * 获取视频列表<hr/>
     * <ul>
     *     <li>时间顺序从新到旧</li>
     * </ul>
     *
     * @return 分页视频列表
     */
    public PaginationResponseVO <BriefVideoInfoVO> loadVideo(String categoryNumber, Integer pageNo, Boolean isRecommend)
    {

        VideoInfoQuery infoQuery = new VideoInfoQuery();
        infoQuery.setOrderBy("create_time desc");
        if (isRecommend != null)
        {
            infoQuery.setRecommendType(isRecommend ? RecommendType.RECOMMENDED.getType() : RecommendType.UNRECOMMENDED.getType());
        }

        if (categoryNumber != null)
        {
            CategoryInfo categoryInfo = selectCategoryInfoByNumber(categoryNumber);
            if (categoryInfo == null)
            {
                throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
            }
            else
            {
                // 如果查询一个一级分类
                if (categoryInfo.getPCategoryId() == 0)
                {
                    // 则将子分类页查询
                    infoQuery.setPCategoryId(categoryInfo.getCategoryId());
                    infoQuery.setCategoryId(categoryInfo.getCategoryId());
                    infoQuery.setFuzzyCategory(true);
                }
                // 如果查询二级分类，精确查询
                else
                {
                    infoQuery.setPCategoryId(categoryInfo.getPCategoryId());
                    infoQuery.setCategoryId(categoryInfo.getCategoryId());
                }

            }
        }

        Integer count = videoInfoMapper.selectCount(infoQuery);
        PageCalculator pageCalculator = new PageCalculator(pageNo, count, webConfig.getPageSize());
        infoQuery.setPageCalculator(pageCalculator);
        List <BriefVideoInfoVO> briefVideoInfoVOList = videoInfoMapper.selectBriefVoListByParam(infoQuery);
        fillCategoryNumbers(briefVideoInfoVOList);
        return new PaginationResponseVO <>(count,
                                           pageCalculator.getPageSize(),
                                           pageNo,
                                           pageCalculator.getPageTotal(),
                                           briefVideoInfoVOList);
    }

    /**
     * 获取视频详细信息
     *
     * @param userId  用户ID
     * @param videoId 视频ID
     * @return 视频VO
     */
    public VideoInfoVO loadVideoInfo(Long userId, long videoId)
    {
        VideoInfoVO videoInfoVO = videoInfoMapper.selectVoByVideoId(videoId);
        if (videoInfoVO == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        Long videoUserId = videoInfoVO.getUserInfo().getUserId();
        if (userId != null)
        {
            // 校验 userId 是否存在
            UserInfo userInfo = userInfoMapper.selectByUserId(userId);
            if (userInfo == null)
            {
                throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
            }

            // 查找关注信息
            FollowInfo followInfo = followInfoMapper.selectByFollowerUserIdAndFollowingUserId(userId, videoUserId);
            videoInfoVO.getUserInfo().setHasFollowed(followInfo != null);
        }
        videoInfoVO.getUserInfo().setFollowerCount(followInfoMapper.selectFollowerCount(videoUserId));
        // 将分类ID转换为分类编码（利用缓存，避免暴露内部ID）
        if (videoInfoVO.getCategoryId() != null)
        {
            CategoryInfo category = selectCategoryInfoById(videoInfoVO.getCategoryId());
            if (category != null)
            {
                videoInfoVO.setCategoryNumber(category.getCategoryNumber());
            }
        }
        if (videoInfoVO.getPCategoryId() != null)
        {
            CategoryInfo pCategory = selectCategoryInfoById(videoInfoVO.getPCategoryId());
            if (pCategory != null)
            {
                videoInfoVO.setPCategoryNumber(pCategory.getCategoryNumber());
            }
        }

        return videoInfoVO;
    }

    /**
     * 获取分P文件信息
     *
     * @param videoId 视频ID
     * @return 所有分P文件信息
     */
    public List <VideoInfoFileVO> loadVideoFile(long videoId)
    {
        List <VideoInfoFileVO> partitionFiles = videoInfoFileMapper.selectVoByVideoId(videoId);
        if (partitionFiles == null || partitionFiles.isEmpty())
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        if (videoInfo == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // sort list in ascending order
        return partitionFiles.stream().sorted(Comparator.comparing((VideoInfoFileVO::getFileIndex))).toList();
    }

    /**
     * 加载热门视频<hr/>
     *
     * @return 热门视频列表
     */
    public List <BriefVideoInfoVO> loadHotVideos(int pageNo)
    {
        List <Long> videoIdList = hotVideoRedisRepository.getHotVideoIdList(pageNo);
        List <BriefVideoInfoVO> voList = videoInfoMapper.selectBriefVoListByVideoIdBatch(videoIdList);
        if (voList == null || voList.isEmpty())
        {
            return List.of();
        }
        else
        {
            return voList;
        }
    }

    /**
     * 增加视频播放量
     *
     * @param videoId 视频ID
     */
    public void playCount(long videoId)
    {
        playCountBuffer.offer(videoId);
    }

    /**
     * Select CategoryInfo by categoryNumber
     *
     * @param categoryNumber categoryNumber
     * @return CategoryInfo
     */
    private CategoryInfo selectCategoryInfoByNumber(String categoryNumber)
    {
        checkCache();
        List <CategoryInfo> categoryInfoList = categoryRedisRepository.getCategoryInfo();
        return categoryInfoList.stream()
                               .filter(categoryInfo -> categoryInfo.getCategoryNumber().equals(categoryNumber))
                               .findFirst()
                               .orElse(null);
    }

    /**
     * Select CategoryInfo by categoryId
     *
     * @param categoryId 分类ID
     * @return CategoryInfo
     */
    private CategoryInfo selectCategoryInfoById(Integer categoryId)
    {
        checkCache();
        List <CategoryInfo> categoryInfoList = categoryRedisRepository.getCategoryInfo();
        return categoryInfoList.stream()
                               .filter(categoryInfo -> categoryId.equals(categoryInfo.getCategoryId()))
                               .findFirst()
                               .orElse(null);
    }

    /**
     * 批量将 BriefVideoInfoVO 列表中的分类ID转换为分类编码
     *
     * @param list BriefVideoInfoVO 列表
     */
    private void fillCategoryNumbers(List <BriefVideoInfoVO> list)
    {
        if (list == null || list.isEmpty()) return;
        for (BriefVideoInfoVO vo : list)
        {
            if (vo.getCategoryId() != null)
            {
                CategoryInfo category = selectCategoryInfoById(vo.getCategoryId());
                if (category != null) vo.setCategoryNumber(category.getCategoryNumber());
            }
            if (vo.getPCategoryId() != null)
            {
                CategoryInfo pCategory = selectCategoryInfoById(vo.getPCategoryId());
                if (pCategory != null) vo.setPCategoryNumber(pCategory.getCategoryNumber());
            }
        }
    }

    /**
     * 检查分类缓存是否存在，如果不存在则刷新缓存
     */
    private void checkCache()
    {
        if (categoryRedisRepository.getCategoryInfo() == null)
        {
            RLock lock = redisson.getLock(CATEGORY_UPDATE_LOCK);
            boolean locked = false;
            try
            {
                locked = lock.tryLock(5, 20, TimeUnit.SECONDS);
                if (locked && categoryRedisRepository.getCategoryInfo() == null) // 抢到锁了，进行第二次检查
                {
                    CategoryInfoQuery param = new CategoryInfoQuery();
                    param.setOrderBy("sort asc");
                    List <CategoryInfo> list = categoryInfoMapper.selectList(param);
                    categoryRedisRepository.setCategory(list);
                }
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
            finally
            {
                if (locked)
                {
                    if (lock.isHeldByCurrentThread())
                    {
                        lock.unlock();
                    }
                    else
                    {
                        log.warn("RLock在业务完成前释放");
                    }
                }
            }
        }
    }

    /**
     * 定时将新统计到的播放信息记录到mysql中，并交给redis处理
     */
    @Scheduled(fixedRateString = "#{@webConfig.playCountRefreshInterval}")
    private void flushPlayCount()
    {
        if (playCountBuffer.isEmpty())
        {
            return;
        }

        // 准备发送给redis的播放数统计，key为videoId，value为播放数增量
        Map <Long, Integer> playCountMap = new HashMap <>();

        Long bufferedVideoId;
        while ((bufferedVideoId = playCountBuffer.poll()) != null)
        {
            playCountMap.merge(bufferedVideoId, 1, Integer::sum);
        }

        // 如果有消息可发，则发送给redis，并保存到mysql中
        if (!playCountMap.isEmpty())
        {
            int size = playCountMap.size();
            List <Long> videoIdList = playCountMap.keySet().stream().toList();

            // 每次最多发送固定条数，在java端做好削峰
            for (int start = 0 ; start < size ; start += batchSize)
            {
                int end = Math.min(start + batchSize, size);

                // 组装batch
                Map <Long, Integer> batch = new HashMap <>();

                for (int i = start ; i < end ; i++)
                {
                    long videoId = videoIdList.get(i);
                    int increment = playCountMap.get(videoId);
                    batch.put(videoId, increment);
                }

                // 保存不可变快照，然后提交给异步方法
                Map <Long, Integer> batchSnapshot = Map.copyOf(batch);

                // 异步更新mysql播放量
                CompletableFuture <Void> mysqlCompletableFuture = videoAsyncService.flushPlayCountBatchToMysql(batchSnapshot);

                // 异步更新redis活跃视频统计数据
                CompletableFuture <Void> redisCompletableFuture = videoAsyncService.flushPlayCountBatchToRedis(batchSnapshot);

                // 异步更新ES播放量
                CompletableFuture <Void> esCompletableFuture = videoAsyncService.flushPlayCountToES(batch);

                // 等异步更新结束看看有没有错误
                CompletableFuture.allOf(mysqlCompletableFuture, redisCompletableFuture, esCompletableFuture).exceptionally(e ->
                                                                                                                           {
                                                                                                                               log.warn(
                                                                                                                                       "异步刷新播放数据时产生异常：{}",
                                                                                                                                       e.toString());
                                                                                                                               return null;
                                                                                                                           });
            }
        }
    }
}
