package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.CategoryService;
import com.neon.nilocommon.entity.po.CategoryInfo;
import com.neon.nilocommon.entity.query.CategoryInfoQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
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

    // TODO 利用缓存，设计一个获取全部分类接口

    /**
     * 根据条件分页查询
     */
    @Operation(summary = "分页获取分类", description = "如果你想不分层地查询分类，你可以使用这个方法")
    @PostMapping("/categories") //分页查询应该用POST
    public ResponseVO <List <CategoryInfo>> getCategories(@RequestBody @Valid @NotNull CategoryInfoQuery query)
    {
        query.setOrderBy("sort asc");
        List <CategoryInfo> dtoList = service.findListByPage(query).getList();
        return ResponseVO.success(dtoList);
    }

    /**
     * 获取指定分类及其子分类（只能获取下面一层）
     *
     * @param parentIds 指定分类id
     * @return 指定分类及其子分类
     */
    @Operation(summary = "分层获取分类", description = "如果你想获取指定分类和其子分类，则可以使用方法")
    @GetMapping(path = "/categories")
    public ResponseVO <List <CategoryInfo>> getCategoriesWithChildren(
            @Parameter(name = "parentIds", description = "所有要获取的分类id") @RequestParam(name = "parentIds") @NotNull
            List <Integer> parentIds)
    {
        return ResponseVO.success(service.findListWithChildren(parentIds));
    }

    /**
     * 保存一个分类<hr/>
     * 可能是新分类，也可能是在旧分类上进行修改
     */
    @Operation(summary = "增加或修改分类", description = "如果要修改分类，必须指定id")
    @PutMapping(path = "/category")
    public ResponseVO <Object> saveCategory(@RequestBody @Valid @NotNull CategoryInfo categoryInfo)
    {
        service.saveCategory(categoryInfo);
        return ResponseVO.success(null);
    }

    /**
     * 删除一个分类
     */
    @Operation(summary = "删除分类")
    @DeleteMapping(path = "/category")
    public ResponseVO <Object> deleteCategory(@RequestParam(name = "id") @NotNull Integer id)
    {
        //TODO 如果分类下有视频，则不能删除分类
        service.deleteCategory(id);
        return ResponseVO.success(null);
    }

    /**
     * 重新排序分类
     */
    @Operation(summary = "重新排序分类")
    @PostMapping(path = "/sortCategory")
    public ResponseVO <Object> sortCategory(@RequestParam(name = "categoryIds") List <Integer> categoryIds,
                                            @RequestParam(name = "parentId") Integer parentId) // 这里加上parentId是防止接口输入成不同分类下的子分类
    {
        service.sortCategory(categoryIds, parentId);
        return ResponseVO.success(null);
    }

}