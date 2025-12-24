package com.neon.niloadmin.service;


import com.neon.niloadmin.mapper.CategoryInfoMapper;
import com.neon.nilocommon.entity.enums.PageSize;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.nilocommon.entity.query.PageCalculator;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.RedisQueryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.neon.nilocommon.entity.constants.Constants.REDIS_KEY_CATEGORIES_INFO;
import static com.neon.nilocommon.entity.constants.Constants.REDIS_CATEGORY_UPDATE_LOCK;


/**
 * 分类信息 业务接口实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CategoryService
{

    private final CategoryInfoMapper <CategoryInfo, CategoryInfoQuery> mapper;

    private final RedisTemplate <String, Object> redisTemplate;

    private final RedissonClient redisson;

    /**
     * 分页查询方法
     */
    public PaginationResponseVO <CategoryInfo> findListByPage(CategoryInfoQuery param)
    {
        checkCache();
        if (redisTemplate.hasKey(REDIS_KEY_CATEGORIES_INFO)) // Redis
        {
            List <CategoryInfo> categoryList = (ArrayList <CategoryInfo>) redisTemplate.opsForValue().get(REDIS_KEY_CATEGORIES_INFO);
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
            List <CategoryInfo> list = this.findListByParam(param);
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
        if (redisTemplate.hasKey(REDIS_KEY_CATEGORIES_INFO))
        {
            list = (ArrayList <CategoryInfo>) redisTemplate.opsForValue().get(REDIS_KEY_CATEGORIES_INFO);
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
        if (redisTemplate.hasKey(REDIS_KEY_CATEGORIES_INFO))
        {
            list = (ArrayList <CategoryInfo>) redisTemplate.opsForValue().get(REDIS_KEY_CATEGORIES_INFO);
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
     * @return categoryId
     */
    public void saveCategory(CategoryInfo categoryInfo)
    {
        // category_number是唯一的
        CategoryInfo existedInfo = mapper.selectByCategoryNumber(categoryInfo.getCategoryNumber());
        // 如果id为null，说明想要新增数据
        // 如果id不为null，说明要更新数据
        if (existedInfo != null && (categoryInfo.getCategoryId() == null || existedInfo.getCategoryId() != categoryInfo.getCategoryId()))
            throw new BusinessException(1000, "分类编号已存在");
        Integer maxSort = mapper.selectMaxSort(categoryInfo.getPCategoryId());
        if (maxSort == null) maxSort = 1;
        categoryInfo.setSort(maxSort + 1);
        if (existedInfo == null) mapper.insert(categoryInfo);
        else mapper.updateByCategoryId(categoryInfo, existedInfo.getCategoryId());
        redisTemplate.delete(REDIS_KEY_CATEGORIES_INFO);
    }

    /**
     * 删除分类及其子分类
     */
    public void deleteCategory(Integer categoryId)
    {
        mapper.deleteByCategoryId(categoryId);
        mapper.deleteByPCategoryId(categoryId);
        redisTemplate.delete(REDIS_KEY_CATEGORIES_INFO);
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
        redisTemplate.delete(REDIS_KEY_CATEGORIES_INFO);
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
        if (!redisTemplate.hasKey(REDIS_KEY_CATEGORIES_INFO))
        {
            RLock lock = redisson.getLock(REDIS_CATEGORY_UPDATE_LOCK);
            boolean locked = false;
            try
            {
                locked = lock.tryLock(5, 20, TimeUnit.SECONDS);
                if (locked && !redisTemplate.hasKey(REDIS_KEY_CATEGORIES_INFO)) // 抢到锁了，进行第二次检查
                {
                    CategoryInfoQuery param = new CategoryInfoQuery();
                    param.setOrderBy("sort asc");
                    List <CategoryInfo> list = mapper.selectList(param);
                    redisTemplate.opsForValue().set(REDIS_KEY_CATEGORIES_INFO, list);
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