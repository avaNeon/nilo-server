package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.po.VideoSeriesVideo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户视频合集视频信息 数据库操作接口
 */
public interface VideoSeriesVideoMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 将合集中的视频列表重新排序
     *
     * @param seriesId             合集ID
     * @param videoSeriesVideoList 新的顺序的视频列表
     * @return 更改行数
     */
    Integer resort(@Param("seriesId") Long seriesId, @Param("videoSeriesVideoList") List <VideoSeriesVideo> videoSeriesVideoList);

    /**
     * 查询指定合集下所有视频最大排序序号
     *
     * @param seriesId 合集ID
     * @return 最大排序序号
     */
    Integer selectMaxSortIndex(@Param("seriesId") Long seriesId);

    /**
     * 删除所有指定合集下的视频记录
     *
     * @param userId   用户ID
     * @param seriesId 合集ID
     * @return 删除行数
     */
    Integer deleteByUserIdAndSeriesId(@Param("userId") Long userId, @Param("seriesId") Long seriesId);

    /**
     * 根据SeriesIdAndVideoId更新
     */
    Integer updateBySeriesIdAndVideoId(@Param("bean") T t, @Param("seriesId") Long seriesId, @Param("videoId") Long videoId);


    /**
     * 根据SeriesIdAndVideoId删除
     */
    Integer deleteBySeriesIdAndVideoId(@Param("seriesId") Long seriesId, @Param("videoId") Long videoId);


    /**
     * 根据SeriesIdAndVideoId获取对象
     */
    T selectBySeriesIdAndVideoId(@Param("seriesId") Long seriesId, @Param("videoId") Long videoId);
}
