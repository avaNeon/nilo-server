package com.neon.niloweb.mapper;


import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 基础通用CRUD接口
 * @param <T> 返回数据类型
 * @param <P> 传入参数类型（必须为xxxQuery类型）
 */
public interface BaseMapper<T, P>
{
    /**
     * selectList:(根据参数查询集合)
     */
    List <T> selectList(@Param("query") P p);

    /**
     * selectCount:(根据集合查询数量)
     */
    Integer selectCount(@Param("query") P p);

    /**
     * insert:(插入)
     */
    Integer insert(@Param("bean") T t);

    /**
     * insertOrUpdate:(插入或者更新)
     */
    Integer insertOrUpdate(@Param("bean") T t);

    /**
     * insertBatch:(批量插入)
     */
    Integer insertBatch(@Param("list") List <T> list);

    /**
     * insertOrUpdateBatch:(批量插入或更新)
     */
    Integer insertOrUpdateBatch(@Param("list") List <T> list);

    /**
     * updateByParams:(多条件更新)
     */
    Integer updateByParam(@Param("bean") T t, @Param("query") P p);

    /**
     * deleteByParam:(多条件删除)
     */
    Integer deleteByParam(@Param("query") P p);

}
