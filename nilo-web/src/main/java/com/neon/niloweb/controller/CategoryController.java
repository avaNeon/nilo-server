package com.neon.niloweb.controller;


import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "分类管理")
@RequiredArgsConstructor
@RequestMapping(path = "/category")
@RestController
public class CategoryController
{
    private final CategoryService service;

    /**
     * 获取所有分类及其子分类（只能获取下面一层）
     *
     * @return 指定分类及其子分类
     */
    @Operation(summary = "分层获取所有分类")
    @GetMapping(path = "/categories/all")
    public ResponseVO <List <CategoryInfo>> getAllCategoriesWithChildren()
    {
        return ResponseVO.success(service.findAllWithChildren());
    }


}