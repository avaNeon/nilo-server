package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.CategoryInfoService;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类信息 Controller
 */
@RequiredArgsConstructor
//@RestController
//@RequestMapping("/category")
public class CategoryInfoController
{
    private final CategoryInfoService categoryInfoService;

    /**
     * 根据条件分页查询
     */
    @RequestMapping("/loadDataList")
    public ResponseVO <Object> loadDataList(CategoryInfoQuery query)
    {
        return ResponseVO.success(categoryInfoService.findListByPage(query));
    }

    /**
     * 新增
     */
    @RequestMapping("/add")
    public ResponseVO <Object> add(CategoryInfo bean)
    {
        categoryInfoService.add(bean);
        return ResponseVO.success(null);
    }

    /**
     * 批量新增
     */
    @RequestMapping("/addBatch")
    public ResponseVO <Object> addBatch(@RequestBody List <CategoryInfo> listBean)
    {
        categoryInfoService.addBatch(listBean);
        return ResponseVO.success(null);
    }

    /**
     * 批量新增/修改
     */
    @RequestMapping("/addOrUpdateBatch")
    public ResponseVO <Object> addOrUpdateBatch(@RequestBody List <CategoryInfo> listBean)
    {
        categoryInfoService.addBatch(listBean);
        return ResponseVO.success(null);
    }

    /**
     * 根据CategoryId查询对象
     */
    @RequestMapping("/getCategoryInfoByCategoryId")
    public ResponseVO <Object> getCategoryInfoByCategoryId(Integer categoryId)
    {
        return ResponseVO.success(categoryInfoService.getCategoryInfoByCategoryId(categoryId));
    }

    /**
     * 根据CategoryId修改对象
     */
    @RequestMapping("/updateCategoryInfoByCategoryId")
    public ResponseVO <Object> updateCategoryInfoByCategoryId(CategoryInfo bean, Integer categoryId)
    {
        categoryInfoService.updateCategoryInfoByCategoryId(bean, categoryId);
        return ResponseVO.success(null);
    }

    /**
     * 根据CategoryId删除
     */
    @RequestMapping("/deleteCategoryInfoByCategoryId")
    public ResponseVO <Object> deleteCategoryInfoByCategoryId(Integer categoryId)
    {
        categoryInfoService.deleteCategoryInfoByCategoryId(categoryId);
        return ResponseVO.success(null);
    }

    /**
     * 根据CategoryNumber查询对象
     */
    @RequestMapping("/getCategoryInfoByCategoryNumber")
    public ResponseVO <Object> getCategoryInfoByCategoryNumber(String categoryNumber)
    {
        return ResponseVO.success(categoryInfoService.getCategoryInfoByCategoryNumber(categoryNumber));
    }

    /**
     * 根据CategoryNumber修改对象
     */
    @RequestMapping("/updateCategoryInfoByCategoryNumber")
    public ResponseVO <Object> updateCategoryInfoByCategoryNumber(CategoryInfo bean, String categoryNumber)
    {
        categoryInfoService.updateCategoryInfoByCategoryNumber(bean, categoryNumber);
        return ResponseVO.success(null);
    }

    /**
     * 根据CategoryNumber删除
     */
    @RequestMapping("/deleteCategoryInfoByCategoryNumber")
    public ResponseVO <Object> deleteCategoryInfoByCategoryNumber(String categoryNumber)
    {
        categoryInfoService.deleteCategoryInfoByCategoryNumber(categoryNumber);
        return ResponseVO.success(null);
    }
}