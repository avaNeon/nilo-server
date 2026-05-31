package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.po.VideoSeriesInfo;
import com.neon.nilocommon.entity.vo.videoSeriesInfo.VideoSeriesInfoVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户视频合集信息 数据库操作接口
 */
public interface VideoSeriesInfoMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 根据用户ID查询最大排序序号
     *
     * @param userId 用户ID
     * @return 最大排序序号
     */
    Integer selectMaxSortIndexByUserId(@Param("userId") Long userId);

    /**
     * 查询合集信息列表，每个合集信息附带第一个合集视频的封面
     *
     * @param userId   用户ID
     * @param start    起始记录序号
     * @param pageSize 页大小
     * @return 合集记录
     */
    List <VideoSeriesInfoVO> selectVideoSeriesInfoVoWithCoverByUserId(@Param("userId") Long userId,
                                                                      @Param("start") Integer start,
                                                                      @Param("pageSize") Integer pageSize);

    /**
     * 根据userId和seriesId删除合集
     *
     * @param userId   用户ID
     * @param seriesId 合集ID
     * @return 删除行数
     */
    Integer deleteByUserIdAndSeriesId(@Param("userId") Long userId, @Param("seriesId") Long seriesId);

    /**
     * 将合集重新排序
     *
     * @param userId              用户ID
     * @param videoSeriesInfoList 合集列表
     * @return 修改行数
     */
    Integer resort(@Param("userId") Long userId, @Param("videoSeriesInfoList") List <VideoSeriesInfo> videoSeriesInfoList);

    /**
     * 根据userId和seriesId查询记录行数
     *
     * @param userId       用户ID
     * @param seriesIdList 合集ID列表
     * @return 记录行数
     */
    Integer selectCountByUserIdAndSeriesIdList(@Param("userId") Long userId, @Param("seriesIdList") List <Long> seriesIdList);

    /**
     * 根据userId查询VideoSeriesInfo列表
     *
     * @param userId   用户ID
     * @param pageSize 限制长度
     * @return VideoSeriesInfo列表
     */
    List <VideoSeriesInfo> selectVideoSeriesListByUserId(@Param("userId") Long userId, @Param("pageSize") Integer pageSize);

    /**
     * 根据SeriesId更新
     */
    Integer updateBySeriesId(@Param("bean") T t, @Param("seriesId") Long seriesId);

    /**
     * 根据SeriesId删除
     */
    Integer deleteBySeriesId(@Param("seriesId") Long seriesId);

    /**
     * 根据SeriesId获取对象
     */
    T selectBySeriesId(@Param("seriesId") Long seriesId);
}
