package com.neon.niloweb.service;


import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.nilocommon.entity.vo.CategoryInfoVO;
import com.neon.niloweb.mapper.CategoryInfoMapper;
import com.neon.niloweb.repository.redis.CategoryRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private final RedissonClient redisson;

    private final CategoryRedisRepository categoryRedisRepository;

    /**
     * 查询所有分类，分类下一级的子分类会被加入children属性中
     */
    public List <CategoryInfoVO> findAllWithChildren()
    {
        checkCache();
        List <CategoryInfo> list = categoryRedisRepository.getCategoryInfo();
        if (list == null)
        {
            list = mapper.selectList(new CategoryInfoQuery());
        }
        return convertToVOList(buildTree(list, 0));
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
     * 将 CategoryInfo 列表转换为 CategoryInfoVO 列表（含子分类递归转换）
     */
    private List <CategoryInfoVO> convertToVOList(List <CategoryInfo> list)
    {
        if (list == null) return new ArrayList <>();
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 将单个 CategoryInfo 转换为 CategoryInfoVO
     */
    private CategoryInfoVO convertToVO(CategoryInfo info)
    {
        CategoryInfoVO vo = new CategoryInfoVO();
        vo.setCategoryNumber(info.getCategoryNumber());
        vo.setCategoryName(info.getCategoryName());
        vo.setIcon(info.getIcon());
        vo.setBackground(info.getBackground());
        vo.setColor(info.getColor());
        vo.setSort(info.getSort());
        if (info.getChildren() != null)
        {
            vo.setChildren(convertToVOList(info.getChildren()));
        }
        return vo;
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
                    List <CategoryInfo> list = mapper.selectList(param);
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