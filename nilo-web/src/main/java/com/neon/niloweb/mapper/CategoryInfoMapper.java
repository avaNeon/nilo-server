package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.po.CategoryInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分类信息 数据库操作接口
 */
public interface CategoryInfoMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 查询分类在父分类下的最大序号
     *
     * @param parentId 父分类id
     * @return 最大sort
     */
    Integer selectMaxSort(@Param("parentId") Integer parentId);

    Integer deleteByPCategoryId(@Param("parentId") Integer parentId);

    List <CategoryInfo> selectByIdOrParentIds(@Param("idOrParentIds") List <Integer> idOrParentIds);

    void updateSort(@Param("categories") List <CategoryInfo> categories);

    /**
     * 根据CategoryId更新
     */
    Integer updateByCategoryId(@Param("bean") T t, @Param("categoryId") Integer categoryId);


    /**
     * 根据CategoryId删除
     */
    Integer deleteByCategoryId(@Param("categoryId") Integer categoryId);


    /**
     * 根据CategoryId获取对象
     */
    T selectByCategoryId(@Param("categoryId") Integer categoryId);


    /**
     * 根据CategoryNumber更新
     */
    Integer updateByCategoryNumber(@Param("bean") T t, @Param("categoryNumber") String categoryNumber);


    /**
     * 根据CategoryNumber删除
     */
    Integer deleteByCategoryNumber(@Param("categoryNumber") String categoryNumber);


    /**
     * 根据CategoryNumber获取对象
     */
    T selectByCategoryNumber(@Param("categoryNumber") String categoryNumber);


}
