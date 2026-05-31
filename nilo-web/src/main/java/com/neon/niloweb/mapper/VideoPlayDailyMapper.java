package com.neon.niloweb.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 数据库操作接口
 */
public interface VideoPlayDailyMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 根据StatisticsDateAndVideoId更新
     */
    Integer updateByStatisticsDateAndVideoId(@Param("bean") T t,
                                             @Param("statisticsDate") LocalDate statisticsDate,
                                             @Param("videoId") Long videoId);


    /**
     * 根据StatisticsDateAndVideoId删除
     */
    Integer deleteByStatisticsDateAndVideoId(@Param("statisticsDate") LocalDate statisticsDate, @Param("videoId") Long videoId);


    /**
     * 根据StatisticsDateAndVideoId获取对象
     */
    T selectByStatisticsDateAndVideoId(@Param("statisticsDate") LocalDate statisticsDate, @Param("videoId") Long videoId);
}
