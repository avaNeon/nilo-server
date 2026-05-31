package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.vo.StatisticsInfoVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据统计 数据库操作接口
 */
public interface StatisticsInfoMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 根据统计日期和用户ID查找统计数据
     *
     * @param statisticsDateBegin 统计开始日期
     * @param statisticsDateEnd   统计结束日期
     * @param userId              用户ID
     * @return 统计数据
     */
    List <StatisticsInfoVO> selectVoByUserIdAndStatisticsDatePeriod(@Param("userId") Long userId,
                                                                    @Param("statisticsDateBegin") LocalDate statisticsDateBegin,
                                                                    @Param("statisticsDateEnd") LocalDate statisticsDateEnd);


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
