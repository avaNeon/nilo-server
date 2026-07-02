package com.neon.niloadmin.mapper;

import com.neon.nilocommon.entity.dto.VideoInfoArchiveAdminJoinDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 删除视频存档 数据库操作接口
 */
public interface VideoInfoArchiveMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 根据VideoId更新
     */
    Integer updateByVideoId(@Param("bean") T t, @Param("videoId") Long videoId);


    /**
     * 根据VideoId删除
     */
    Integer deleteByVideoId(@Param("videoId") Long videoId);


    /**
     * 根据VideoId获取对象
     */
    T selectByVideoId(@Param("videoId") Long videoId);

    /**
     * 通用查询列表，关联UserInfo表
     */
    List <VideoInfoArchiveAdminJoinDTO> selectListWithUserInfo(@Param("query") P query);


}
