package com.neon.niloadmin.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据库操作接口
 */
public interface MediaOwnershipMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 根据ObjectKey更新
     */
    Integer updateByObjectKey(@Param("bean") T t, @Param("objectKey") String objectKey);


    /**
     * 根据ObjectKey删除
     */
    Integer deleteByObjectKey(@Param("objectKey") String objectKey);

    /**
     * 根据ObjectKey批量删除
     */
    Integer deleteBatchByObjectKey(@Param("objectKeys") List <String> objectKeys);


    /**
     * 根据ObjectKey获取对象
     */
    T selectByObjectKey(@Param("objectKey") String objectKey);

    /**
     * 根据Id更新
     */
    Integer updateById(@Param("bean") T t, @Param("id") Long id);


    /**
     * 根据Id删除
     */
    Integer deleteById(@Param("id") Long id);


    /**
     * 根据Id获取对象
     */
    T selectById(@Param("id") Long id);
}
