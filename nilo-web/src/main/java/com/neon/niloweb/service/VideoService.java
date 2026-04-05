package com.neon.niloweb.service;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoInfo.RecommendType;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.po.VideoInfoFile;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.entity.vo.BriefVideoInfoVO;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.entity.vo.VideoInfoFileVO;
import com.neon.nilocommon.entity.vo.VideoInfoVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.CategoryInfoMapper;
import com.neon.niloweb.mapper.UserInfoMapper;
import com.neon.niloweb.mapper.VideoInfoFileMapper;
import com.neon.niloweb.mapper.VideoInfoMapper;
import com.neon.niloweb.repository.redis.CategoryRedisRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.neon.nilocommon.entity.constants.RedisKey.CATEGORY_UPDATE_LOCK;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoService
{

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    private final UserInfoMapper<UserInfo, UserInfoQuery>  userInfoMapper;

    private final CategoryRedisRepository categoryRedisRepository;

    private final CategoryInfoMapper <CategoryInfo, CategoryInfoQuery> categoryInfoMapper;

    private final WebConfig webConfig;

    private final RedissonClient redisson;

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
     * @param videoId 视频ID
     * @return 视频VO
     */
    public VideoInfoVO loadVideoInfo(long videoId)
    {
        VideoInfoVO videoInfoVO = videoInfoMapper.selectVoByVideoId(videoId);
        if (videoInfoVO == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
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
        //todo 获取用户行为：点赞、投币、收藏
        return videoInfoVO;
    }

    /**
     * 获取分P文件信息
     *
     * @param videoId 视频ID
     * @return 所有分P文件信息
     */
    public List <VideoInfoFileVO> loadVideoFile(@NotNull Long videoId)
    {
        List <VideoInfoFileVO> partitionFiles = videoInfoFileMapper.selectVoByVideoID(videoId);
        if (partitionFiles == null || partitionFiles.isEmpty())
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        return partitionFiles;
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
}
