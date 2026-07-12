package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.po.VideoInfoFile;
import com.neon.nilocommon.entity.vo.VideoInfoFileVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 视频文件信息 数据库操作接口
 */
public interface VideoInfoFileMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 根据视频ID获取文件信息
     *
     * @param videoId 视频ID
     * @return 文件信息列表
     */
    List <T> selectByVideoId(@Param("videoId") Long videoId);

    /**
     * 根据videoId获取视频文件VO对象
     *
     * @param videoId 视频ID
     * @return 视频文件VO对象
     */
    List <VideoInfoFileVO> selectVoByVideoId(@Param("videoId") Long videoId);

    VideoInfoFile selectByVideoIdAndFileIndex(@Param("videoId") Long videoId, @Param("fileIndex") Integer fileIndex);

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
