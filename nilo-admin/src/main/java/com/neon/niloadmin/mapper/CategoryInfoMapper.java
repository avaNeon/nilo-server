package com.neon.niloadmin.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 分类信息 数据库操作接口
 */
public interface CategoryInfoMapper<T, P> extends BaseMapper <T, P>
{

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
