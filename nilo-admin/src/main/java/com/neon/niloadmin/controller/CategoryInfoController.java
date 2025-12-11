package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.CategoryService;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 分类信息 Controller
 */
@RequiredArgsConstructor
//@RestController
//@RequestMapping("/category")
public class CategoryInfoController
{
    private final CategoryService categoryService;

    /**
     * 根据条件分页查询
     */
    @RequestMapping("/loadDataList")
    public ResponseVO <Object> loadDataList(CategoryInfoQuery query)
    {
        return ResponseVO.success(categoryService.findListByPage(query));
    }

    /**
     * 新增
     */
    @RequestMapping("/add")
    public ResponseVO <Object> add(CategoryInfo bean)
    {
        categoryService.add(bean);
        return ResponseVO.success(null);
    }

    /**
     * 批量新增
     */
    @RequestMapping("/addBatch")
    public ResponseVO <Object> addBatch(@RequestBody List <CategoryInfo> listBean)
    {
        categoryService.addBatch(listBean);
        return ResponseVO.success(null);
    }

    /**
     * 批量新增/修改
     */
    @RequestMapping("/addOrUpdateBatch")
    public ResponseVO <Object> addOrUpdateBatch(@RequestBody List <CategoryInfo> listBean)
    {
        categoryService.addBatch(listBean);
        return ResponseVO.success(null);
    }

    /**
     * 根据CategoryId查询对象
     */
    @RequestMapping("/getCategoryInfoByCategoryId")
    public ResponseVO <Object> getCategoryInfoByCategoryId(Integer categoryId)
    {
        return ResponseVO.success(categoryService.getCategoryInfoByCategoryId(categoryId));
    }

    /**
     * 根据CategoryId修改对象
     */
    @RequestMapping("/updateCategoryInfoByCategoryId")
    public ResponseVO <Object> updateCategoryInfoByCategoryId(CategoryInfo bean, Integer categoryId)
    {
        categoryService.updateCategoryInfoByCategoryId(bean, categoryId);
        return ResponseVO.success(null);
    }

    /**
     * 根据CategoryId删除
     */
    @RequestMapping("/deleteCategoryInfoByCategoryId")
    public ResponseVO <Object> deleteCategoryInfoByCategoryId(Integer categoryId)
    {
        categoryService.deleteCategoryInfoByCategoryId(categoryId);
        return ResponseVO.success(null);
    }

    /**
     * 根据CategoryNumber查询对象
     */
    @RequestMapping("/getCategoryInfoByCategoryNumber")
    public ResponseVO <Object> getCategoryInfoByCategoryNumber(String categoryNumber)
    {
        return ResponseVO.success(categoryService.getCategoryInfoByCategoryNumber(categoryNumber));
    }

    /**
     * 根据CategoryNumber修改对象
     */
    @RequestMapping("/updateCategoryInfoByCategoryNumber")
    public ResponseVO <Object> updateCategoryInfoByCategoryNumber(CategoryInfo bean, String categoryNumber)
    {
        categoryService.updateCategoryInfoByCategoryNumber(bean, categoryNumber);
        return ResponseVO.success(null);
    }

    /**
     * 根据CategoryNumber删除
     */
    @RequestMapping("/deleteCategoryInfoByCategoryNumber")
    public ResponseVO <Object> deleteCategoryInfoByCategoryNumber(String categoryNumber)
    {
        categoryService.deleteCategoryInfoByCategoryNumber(categoryNumber);
        return ResponseVO.success(null);
    }
}