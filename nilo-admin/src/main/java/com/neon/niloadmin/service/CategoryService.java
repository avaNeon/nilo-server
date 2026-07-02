package com.neon.niloadmin.service;


import com.neon.niloadmin.config.AdminConfig;
import com.neon.niloadmin.mapper.CategoryInfoMapper;
import com.neon.niloadmin.mapper.VideoInfoMapper;
import com.neon.niloadmin.repository.rabbitmq.MqRepository;
import com.neon.niloadmin.repository.redis.CategoryRedisRepository;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.enums.PageSize;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.nilocommon.util.RedisQueryUtil;
import com.neon.nilocommon.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.neon.nilocommon.entity.constants.RedisKey.CATEGORY_UPDATE_LOCK;


/**
 * 分类信息 业务接口实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CategoryService
{
    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final CategoryInfoMapper <CategoryInfo, CategoryInfoQuery> mapper;

    private final CategoryRedisRepository categoryRedisRepository;

    private final RedissonClient redisson;

    private final AdminConfig adminConfig;

    private final MqRepository mqRepository;

    /**
     * 分页查询方法
     */
    public PaginationResponseVO <CategoryInfo> findListByPage(CategoryInfoQuery param)
    {
        checkCache();
        List <CategoryInfo> redisCategoryList = categoryRedisRepository.getCategoryInfo();
        if (redisCategoryList != null) // Redis
        {
            List <CategoryInfo> categoryList = redisCategoryList;
            // 1. 先过滤和排序
            categoryList = RedisQueryUtil.filterAndSort(categoryList, param, CategoryInfo.class);

            // 2. 计算过滤后的总数
            int count = categoryList.size();
            int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();
            PageCalculator page = new PageCalculator(param.getPageNo(), count, pageSize);
            // 3. 最后分页
            if (count > 0)
            {
                int fromIndex = page.getStart();
                int toIndex = Math.min(page.getStart() + page.getSize(), count);
                categoryList = categoryList.subList(fromIndex, toIndex);
            }
            else
            {
                categoryList = Collections.emptyList();
            }

            return new PaginationResponseVO <>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), categoryList);
        }
        else // MySQL
        {
            int count = this.findCountByParam(param);
            int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();
            PageCalculator page = new PageCalculator(param.getPageNo(), count, pageSize);
            param.setPageCalculator(page);
            List <CategoryInfo> list = findListByParam(param);
            return new PaginationResponseVO <>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        }
    }

    /**
     * 根据条件查询列表，分类下一级的子分类会被加入children属性中
     */
    public List <CategoryInfo> findListWithChildren(List <Integer> idOrParentIds)
    {
        checkCache();
        List <CategoryInfo> list;
        if (categoryRedisRepository.hasCategoryInfo())
        {
            list = categoryRedisRepository.getCategoryInfo();
            if (list == null) return new ArrayList <>();
            Set <Integer> set = new HashSet <>(idOrParentIds);
            list.removeIf(categoryInfo ->
                          {
                              Integer id = categoryInfo.getCategoryId();
                              Integer pId = categoryInfo.getPCategoryId();
                              return !(id != null && set.contains(id)) && !(pId != null && set.contains(pId));
                          });
        }
        else
        {
            list = mapper.selectByIdOrParentIds(idOrParentIds);
        }
        return buildTree(list, 0);
    }

    /**
     * 查询所有分类，分类下一级的子分类会被加入children属性中
     */
    public List <CategoryInfo> findAllWithChildren()
    {
        checkCache();
        List <CategoryInfo> list;
        if (categoryRedisRepository.hasCategoryInfo())
        {
            list = categoryRedisRepository.getCategoryInfo();
            if (list == null) return new ArrayList <>();
        }
        else
        {
            list = mapper.selectList(new CategoryInfoQuery());
        }
        return buildTree(list, 0);
    }

    /**
     * 保存分类信息
     *
     * @param categoryInfo 分类信息
     */
    public void saveCategory(CategoryInfo categoryInfo)
    {
        // 先校验一下 categoryNumber 唯一性
        CategoryInfo sameNumberInfo = mapper.selectByCategoryNumber(categoryInfo.getCategoryNumber());

        // 如果有其他分类有这个 categoryNumber 禁止保存
        if (sameNumberInfo != null && (sameNumberInfo.getCategoryId() == null || !Objects.equals(sameNumberInfo.getCategoryId(),
                                                                                                 categoryInfo.getCategoryId())))
        {
            throw new BusinessException(1000, "分类编号已存在");
        }

        // 检查是否存在记录
        CategoryInfo existedInfo = mapper.selectByCategoryId(categoryInfo.getCategoryId());

        try
        {
            if (existedInfo == null) // 新增
            {
                // 给一个排序序号
                Integer maxSort = mapper.selectMaxSort(categoryInfo.getPCategoryId());

                if (maxSort == null)
                {
                    maxSort = 1;
                }

                categoryInfo.setSort(maxSort + 1);

                mapper.insert(categoryInfo);
            }
            else // 修改
            {
                mapper.updateByCategoryId(categoryInfo, existedInfo.getCategoryId());


                // 删除旧头像和背景头图
                List <String> deletePathList = new ArrayList <>();
                String fileRootPath = Path.of(adminConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME).normalize().toString();

                String oldIcon = existedInfo.getIcon();
                // 如果旧图标存在
                if (oldIcon != null && !oldIcon.isBlank())
                {
                    String iconAbsPath = Path.of(fileRootPath, Constants.COVER_FOLDER_NAME, oldIcon).normalize().toString();
                    if (StringUtil.isValidPath(fileRootPath, iconAbsPath))
                    {
                        deletePathList.add(iconAbsPath);
                    }
                }

                String oldBackground = existedInfo.getBackground();
                // 如果旧背景存在
                if (oldBackground != null && !oldBackground.isBlank())
                {
                    String bgAbsPath = Path.of(fileRootPath, Constants.COVER_FOLDER_NAME, oldBackground).normalize().toString();
                    if (StringUtil.isValidPath(fileRootPath, bgAbsPath))
                    {
                        deletePathList.add(bgAbsPath);
                    }
                }

                if (!deletePathList.isEmpty())
                {
                    mqRepository.addPathList2DeleteQueue(deletePathList);
                }
            }
        }
        finally
        {
            // 不管有没有执行成功，都删除旧缓存，能立即看到变化
            categoryRedisRepository.deleteCategoryInfo();
        }

    }

    /**
     * 删除分类及其子分类
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Integer categoryId)
    {
        checkCache();
        List <CategoryInfo> categoryInfoList = categoryRedisRepository.getCategoryInfo();

        // 过滤出需要删除的分类列表
        List <CategoryInfo> deletedCategoryList = categoryInfoList.stream()
                                                                  .filter(categoryInfo ->
                                                                                  // 获取删除分类自身及其子分类
                                                                                  categoryInfo.getCategoryId()
                                                                                              .equals(categoryId) || categoryInfo.getPCategoryId()
                                                                                                                                 .equals(categoryId))
                                                                  .toList();

        // 将列表转化为分类ID
        List <Integer> deletedCategoryIdList = deletedCategoryList.stream().map(CategoryInfo::getCategoryId).toList();

        // 如果分类下还有视频，不能删除分类
        Integer count = videoInfoMapper.selectCountByCategoryIdBatch(deletedCategoryIdList);
        if (count > 0)
        {
            throw new BusinessException("现在不能删除，分类下还有视频");
        }

        mapper.deleteByCategoryId(categoryId);
        mapper.deleteByPCategoryId(categoryId);
        categoryRedisRepository.deleteCategoryInfo();

        // 获取根路径
        String fileRootPath = Path.of(adminConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME).normalize().toString();

        // 定义删除列表
        ArrayList <String> deletedPathList = new ArrayList <>();

        // 删除图片
        deletedCategoryList.forEach(c ->
                                    {
                                        String icon = c.getIcon();
                                        String background = c.getBackground();

                                        if (icon != null && !icon.isEmpty())
                                        {
                                            String iconPathStr = Path.of(fileRootPath, Constants.COVER_FOLDER_NAME, icon)
                                                                     .normalize()
                                                                     .toString();
                                            if (StringUtil.isValidPath(fileRootPath, iconPathStr))
                                            {
                                                deletedPathList.add(iconPathStr);
                                            }
                                        }

                                        if (background != null && !background.isEmpty())
                                        {
                                            String backgroundPathStr = Path.of(fileRootPath,
                                                                               Constants.COVER_FOLDER_NAME,
                                                                               background).normalize().toString();
                                            if (StringUtil.isValidPath(fileRootPath, backgroundPathStr))
                                            {
                                                deletedPathList.add(backgroundPathStr);
                                            }
                                        }
                                    });

        // 用MQ队列异步删除图片
        if (!deletedPathList.isEmpty())
        {
            mqRepository.addPathList2DeleteQueue(deletedPathList);
        }
    }

    /**
     * 给分类重新排序，序号从1开始
     */
    public void sortCategory(List <Integer> categoryIds, Integer parentId)
    {
        AtomicInteger count = new AtomicInteger(0);
        List <CategoryInfo> list = categoryIds.stream().map(id ->
                                                            {
                                                                CategoryInfo categoryInfo = new CategoryInfo();
                                                                categoryInfo.setCategoryId(id);
                                                                categoryInfo.setSort(count.incrementAndGet());
                                                                categoryInfo.setPCategoryId(parentId);
                                                                return categoryInfo;
                                                            }).toList();
        mapper.updateSort(list);
        categoryRedisRepository.deleteCategoryInfo();
    }

    /**
     * 根据条件查询列表
     */
    public List <CategoryInfo> findListByParam(CategoryInfoQuery param)
    {
        return this.mapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    public Integer findCountByParam(CategoryInfoQuery param)
    {
        return this.mapper.selectCount(param);
    }

    /**
     * 新增
     */
    public Integer add(CategoryInfo bean)
    {
        return this.mapper.insert(bean);
    }

    /**
     * 将传入的分类依照其id和parentId转换成树形结构
     *
     * @param list     分类列表
     * @param parentId 从哪个parentId开始
     * @return 转换为树形结构的列表
     */
    private List <CategoryInfo> buildTree(List <CategoryInfo> list, int parentId)
    {
        List <CategoryInfo> children = new ArrayList <>();
        for (CategoryInfo categoryInfo : list)
        {
            if (categoryInfo.getPCategoryId().equals(parentId))
            {
                categoryInfo.setChildren(buildTree(list, categoryInfo.getCategoryId()));
                children.add(categoryInfo);
            }
        }
        return children;
    }

    /**
     * 检查缓存中是否有分类缓存
     */
    private void checkCache()
    {
        if (!categoryRedisRepository.hasCategoryInfo())
        {
            RLock lock = redisson.getLock(CATEGORY_UPDATE_LOCK);
            boolean locked = false;
            try
            {
                locked = lock.tryLock(5, 20, TimeUnit.SECONDS);
                if (locked && !categoryRedisRepository.hasCategoryInfo()) // 抢到锁了，进行第二次检查
                {
                    CategoryInfoQuery param = new CategoryInfoQuery();
                    param.setOrderBy("sort asc");
                    List <CategoryInfo> list = mapper.selectList(param);
                    categoryRedisRepository.setCategoryInfo(list);
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