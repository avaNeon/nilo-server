package com.neon.niloadmin.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 视频文件信息 数据库操作接口
 */
public interface VideoInfoFileUploadMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * <h4>根据fileId批量删除在fileIdList指定的fileId</h4><hr/>
     * 注意，这里也必须要需要用户id防止其它用户删除其它的视频提交
     * @param fileIdList 一组fileId列表，制定了要删除的文件对象
     * @return 被删除的记录行数
     */
    Integer deleteBatchByFileId(@Param("fileIdList") List <Long> fileIdList,@Param("userId") Long userId);

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


    /**
     * 根据UploadIdAndUserId更新
     */
    Integer updateByUploadIdAndUserId(@Param("bean") T t, @Param("uploadId") Long uploadId, @Param("userId") Long userId);


    /**
     * 根据UploadIdAndUserId删除
     */
    Integer deleteByUploadIdAndUserId(@Param("uploadId") Long uploadId, @Param("userId") Long userId);


    /**
     * 根据UploadIdAndUserId获取对象
     */
    T selectByUploadIdAndUserId(@Param("uploadId") Long uploadId, @Param("userId") Long userId);


}
