package com.neon.niloweb.service;


import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.niloweb.mapper.CategoryInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.neon.nilocommon.entity.constants.RedisKey.CATEGORIES_INFO;
import static com.neon.nilocommon.entity.constants.RedisKey.CATEGORY_UPDATE_LOCK;


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
     * 查询所有分类，分类下一级的子分类会被加入children属性中
     */
    public List <CategoryInfo> findAllWithChildren()
    {
        checkCache();
        List <CategoryInfo> list;
        if (redisTemplate.hasKey(CATEGORIES_INFO))
        {
            list = (ArrayList <CategoryInfo>) redisTemplate.opsForValue().get(CATEGORIES_INFO);
            if (list == null) return new ArrayList <>();
        }
        else
        {
            list = mapper.selectList(new CategoryInfoQuery());
        }
        return buildTree(list, 0);
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
        if (!redisTemplate.hasKey(CATEGORIES_INFO))
        {
            RLock lock = redisson.getLock(CATEGORY_UPDATE_LOCK);
            boolean locked = false;
            try
            {
                locked = lock.tryLock(5, 20, TimeUnit.SECONDS);
                if (locked && !redisTemplate.hasKey(CATEGORIES_INFO)) // 抢到锁了，进行第二次检查
                {
                    CategoryInfoQuery param = new CategoryInfoQuery();
                    param.setOrderBy("sort asc");
                    List <CategoryInfo> list = mapper.selectList(param);
                    redisTemplate.opsForValue().set(CATEGORIES_INFO, list);
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