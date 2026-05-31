package com.neon.nilomqconsumer.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据统计 数据库操作接口
 */
public interface StatisticsInfoMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * <b>整合每日视频统计</b><hr/>
     * <p> video_play_daily 中相同用户ID的统计将会被合并为一条记录插入 statistics_info</p>
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    Integer reduceVideoPlayDaily(@Param("statisticsDate") LocalDate statisticsDate);

    /**
     * <b>整合每日粉丝统计</b><hr/>
     * <p>将指定日期内各用户新增的粉丝数聚合写入 statistics_info。</p>
     *
     * @param statisticsDate 统计日期
     * @param startDate      统计开始时间，闭区间
     * @param endDate        统计结束时间，开区间
     * @return 改变行数
     */
    Integer reduceDailyFollower(@Param("statisticsDate") LocalDate statisticsDate,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);

    /**
     * <b>整合每日评论统计</b><hr/>
     * <p>将指定日期内各视频作者收到的评论数聚合写入 statistics_info。</p>
     *
     * @param statisticsDate 统计日期
     * @param startDate      统计开始时间，闭区间
     * @param endDate        统计结束时间，开区间
     * @return 改变行数
     */
    Integer reduceDailyComment(@Param("statisticsDate") LocalDate statisticsDate,
                               @Param("startDate") LocalDateTime startDate,
                               @Param("endDate") LocalDateTime endDate);

    /**
     * <b>整合每日弹幕统计</b><hr/>
     * <p>将指定日期内各视频作者收到的弹幕数聚合写入 statistics_info。</p>
     *
     * @param statisticsDate 统计日期
     * @param startDate      统计开始时间，闭区间
     * @param endDate        统计结束时间，开区间
     * @return 改变行数
     */
    Integer reduceDailyDanmaku(@Param("statisticsDate") LocalDate statisticsDate,
                               @Param("startDate") LocalDateTime startDate,
                               @Param("endDate") LocalDateTime endDate);

    /**
     * <b>整合每日视频操作统计</b><hr/>
     * <p>将指定日期内各视频作者收到的点赞、收藏、投币数量聚合写入 statistics_info。</p>
     *
     * @param statisticsDate 统计日期
     * @param startDate      统计开始时间，闭区间
     * @param endDate        统计结束时间，开区间
     * @return 改变行数
     */
    Integer reduceDailyVideoAction(@Param("statisticsDate") LocalDate statisticsDate,
                                   @Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    /**
     * 根据StatisticsDateAndUserIdAndDataType更新
     */
    Integer updateByStatisticsDateAndUserIdAndDataType(@Param("bean") T t,
                                                       @Param("statisticsDate") LocalDate statisticsDate,
                                                       @Param("userId") Long userId,
                                                       @Param("dataType") Integer dataType);


    /**
     * 根据StatisticsDateAndUserIdAndDataType删除
     */
    Integer deleteByStatisticsDateAndUserIdAndDataType(@Param("statisticsDate") LocalDate statisticsDate,
                                                       @Param("userId") Long userId,
                                                       @Param("dataType") Integer dataType);


    /**
     * 根据StatisticsDateAndUserIdAndDataType获取对象
     */
    T selectByStatisticsDateAndUserIdAndDataType(@Param("statisticsDate") LocalDate statisticsDate,
                                                 @Param("userId") Long userId,
                                                 @Param("dataType") Integer dataType);
}
