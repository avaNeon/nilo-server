package com.neon.nilomqconsumer.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 视频文件信息 数据库操作接口
 */
public interface VideoInfoFileUploadMapper<T, P> extends BaseMapper <T, P>
{

    Integer sumDuration(@Param("videoId") Long videoId);

    /**
     * 根据userId和fileId更新
     */
    Integer updateByUserIdAndFileId(@Param("bean") T t, @Param("userId") Long userId, @Param("fileId") Long fileId);

    /**
     * 根据videoId查询
     *
     * @param videoId 视频ID
     * @return 视频文件信息列表
     */
    List <T> selectByVideoId(@Param("videoId") Long videoId);

    /**
     * 根据fileId批量删除在fileIdList指定的fileId<hr/>
     * 注意，这里也必须要需要用户id防止其它用户删除其它的视频提交
     *
     * @param fileIdList 一组fileId列表，制定了要删除的文件对象
     * @return 被删除的记录行数
     */
    Integer deleteBatchByFileId(@Param("fileIdList") List <Long> fileIdList, @Param("userId") Long userId);

    /**
     * 根据FileId删除
     */
    Integer deleteByFileId(@Param("fileId") Long fileId);

    /**
     * 根据FileId获取对象
     */
    T selectByFileId(@Param("fileId") Long fileId);

}
