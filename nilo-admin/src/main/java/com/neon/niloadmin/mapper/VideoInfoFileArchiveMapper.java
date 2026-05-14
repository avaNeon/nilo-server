package com.neon.niloadmin.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 删除视频文件存档 数据库操作接口
 */
public interface VideoInfoFileArchiveMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 根据FileId更新
     */
    Integer updateByFileId(@Param("bean") T t, @Param("fileId") Long fileId);


    /**
     * 根据FileId删除
     */
    Integer deleteByFileId(@Param("fileId") Long fileId);


    /**
     * 根据FileId获取对象
     */
    T selectByFileId(@Param("fileId") Long fileId);


}
