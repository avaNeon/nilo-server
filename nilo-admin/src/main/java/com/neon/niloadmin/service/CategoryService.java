package com.neon.niloadmin.service;


import com.neon.niloadmin.mapper.CategoryInfoMapper;
import com.neon.nilocommon.entity.dto.CategoryDTO;
import com.neon.nilocommon.entity.enums.PageSize;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.nilocommon.entity.query.PageCalculator;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 分类信息 业务接口实现
 */
@RequiredArgsConstructor
@Service
public class CategoryService
{

    private final CategoryInfoMapper <CategoryInfo, CategoryInfoQuery> mapper;

    /**
     * 保存分类信息
     *
     * @param categoryInfo 分类信息
     * @return categoryId
     */
    public Integer saveCategory(CategoryInfo categoryInfo)
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
        return mapper.selectByCategoryNumber(categoryInfo.getCategoryNumber()).getCategoryId();
    }

    /**
     * 删除分类及其子分类
     */
    public void deleteCategory(Integer categoryId)
    {
        mapper.deleteByCategoryId(categoryId);
        mapper.deleteByPCategoryId(categoryId);
    }

    /**
     * 根据条件查询列表，分类下一级的子分类会被加入children属性中
     */
    public List <CategoryDTO> findListWithChildren(List <Integer> idOrParentIds)
    {
        List <CategoryDTO> dtos = mapper.selectByIdOrParentIds(idOrParentIds);
        return buildTree(dtos, 0);
    }

    /**
     * 给分类重新排序，序号从1开始
     */
    public void sortCategory(List <Integer> categoryIds,Integer parentId)
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
     * 分页查询方法
     */
    public PaginationResponseVO <CategoryInfo> findListByPage(CategoryInfoQuery param)
    {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        PageCalculator page = new PageCalculator(param.getPageNo(), count, pageSize);
        param.setPageCalculator(page);
        List <CategoryInfo> list = this.findListByParam(param);
        return new PaginationResponseVO <>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
    }

    /**
     * 新增
     */
    public Integer add(CategoryInfo bean)
    {
        return this.mapper.insert(bean);
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
        return this.mapper.insertBatch(listBean);
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
        return this.mapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    public Integer updateByParam(CategoryInfo bean, @Valid CategoryInfoQuery param)
    {
        return this.mapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    public Integer deleteByParam(@Valid CategoryInfoQuery param)
    {
        return this.mapper.deleteByParam(param);
    }

    /**
     * 根据CategoryId获取对象
     */
    public CategoryInfo getCategoryInfoByCategoryId(Integer categoryId)
    {
        return this.mapper.selectByCategoryId(categoryId);
    }

    /**
     * 根据CategoryId修改
     */
    public Integer updateCategoryInfoByCategoryId(CategoryInfo bean, Integer categoryId)
    {
        return this.mapper.updateByCategoryId(bean, categoryId);
    }

    /**
     * 根据CategoryId删除
     */
    public Integer deleteCategoryInfoByCategoryId(Integer categoryId)
    {
        return this.mapper.deleteByCategoryId(categoryId);
    }

    /**
     * 根据CategoryNumber获取对象
     */
    public CategoryInfo getCategoryInfoByCategoryNumber(String categoryNumber)
    {
        return this.mapper.selectByCategoryNumber(categoryNumber);
    }

    /**
     * 根据CategoryNumber修改
     */
    public Integer updateCategoryInfoByCategoryNumber(CategoryInfo bean, String categoryNumber)
    {
        return this.mapper.updateByCategoryNumber(bean, categoryNumber);
    }

    /**
     * 根据CategoryNumber删除
     */
    public Integer deleteCategoryInfoByCategoryNumber(String categoryNumber)
    {
        return this.mapper.deleteByCategoryNumber(categoryNumber);
    }

    /**
     * 将传入的分类依照其id和parentId转换成树形结构
     *
     * @param list     分类列表
     * @param parentId 从哪个parentId开始
     * @return 转换为树形结构的列表
     */
    private List <CategoryDTO> buildTree(List <CategoryDTO> list, int parentId)
    {
        List <CategoryDTO> children = new ArrayList <>();
        for (CategoryDTO categoryDTO : list)
        {
            if (categoryDTO.getPCategoryId().equals(parentId))
            {
                categoryDTO.setChildren(buildTree(list, categoryDTO.getCategoryId()));
                children.add(categoryDTO);
            }
        }
        return children;
    }
}