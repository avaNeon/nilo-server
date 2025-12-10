package com.neon.niloadmin.service;


import com.neon.niloadmin.mapper.CategoryInfoMapper;
import com.neon.nilocommon.entity.enums.PageSize;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.nilocommon.entity.query.PageCalculator;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 分类信息 业务接口实现
 */
@RequiredArgsConstructor
@Service
public class CategoryInfoService
{

    private final CategoryInfoMapper <CategoryInfo, CategoryInfoQuery> categoryInfoMapper;

    /**
     * 根据条件查询列表
     */

    public List <CategoryInfo> findListByParam(CategoryInfoQuery param)
    {
        return this.categoryInfoMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    public Integer findCountByParam(CategoryInfoQuery param)
    {
        return this.categoryInfoMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    public PaginationResponseVO <CategoryInfo> findListByPage(CategoryInfoQuery param)
    {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        PageCalculator page = new PageCalculator(param.getPageNo(), count, pageSize);
        param.setPageCalculator(page);
        List <CategoryInfo> list = this.findListByParam(param);
        PaginationResponseVO <CategoryInfo> result = new PaginationResponseVO <>(count,
                                                                                 page.getPageSize(),
                                                                                 page.getPageNo(),
                                                                                 page.getPageTotal(),
                                                                                 list);
        return result;
    }

    /**
     * 新增
     */
    public Integer add(CategoryInfo bean)
    {
        return this.categoryInfoMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    public Integer addBatch(List <CategoryInfo> listBean)
    {
        if (listBean == null || listBean.isEmpty())
        {
            return 0;
        }
        return this.categoryInfoMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    public Integer addOrUpdateBatch(List <CategoryInfo> listBean)
    {
        if (listBean == null || listBean.isEmpty())
        {
            return 0;
        }
        return this.categoryInfoMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    public Integer updateByParam(CategoryInfo bean,@Valid CategoryInfoQuery param)
    {
        return this.categoryInfoMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    public Integer deleteByParam(@Valid CategoryInfoQuery param)
    {
        return this.categoryInfoMapper.deleteByParam(param);
    }

    /**
     * 根据CategoryId获取对象
     */
    public CategoryInfo getCategoryInfoByCategoryId(Integer categoryId)
    {
        return this.categoryInfoMapper.selectByCategoryId(categoryId);
    }

    /**
     * 根据CategoryId修改
     */
    public Integer updateCategoryInfoByCategoryId(CategoryInfo bean, Integer categoryId)
    {
        return this.categoryInfoMapper.updateByCategoryId(bean, categoryId);
    }

    /**
     * 根据CategoryId删除
     */
    public Integer deleteCategoryInfoByCategoryId(Integer categoryId)
    {
        return this.categoryInfoMapper.deleteByCategoryId(categoryId);
    }

    /**
     * 根据CategoryNumber获取对象
     */
    public CategoryInfo getCategoryInfoByCategoryNumber(String categoryNumber)
    {
        return this.categoryInfoMapper.selectByCategoryNumber(categoryNumber);
    }

    /**
     * 根据CategoryNumber修改
     */
    public Integer updateCategoryInfoByCategoryNumber(CategoryInfo bean, String categoryNumber)
    {
        return this.categoryInfoMapper.updateByCategoryNumber(bean, categoryNumber);
    }

    /**
     * 根据CategoryNumber删除
     */
    public Integer deleteCategoryInfoByCategoryNumber(String categoryNumber)
    {
        return this.categoryInfoMapper.deleteByCategoryNumber(categoryNumber);
    }
}