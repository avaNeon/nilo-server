package com.neon.niloadmin.service;

import com.neon.niloadmin.mapper.StatisticsInfoMapper;
import com.neon.niloadmin.mapper.UserInfoMapper;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.statisticsInfo.DataType;
import com.neon.nilocommon.entity.po.StatisticsInfo;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.query.StatisticsInfoQuery;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.vo.StatisticsInfoVO;
import com.neon.nilocommon.entity.vo.UserStatVO;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class GlobalStatisticsService
{
    private final StatisticsInfoMapper <StatisticsInfo, StatisticsInfoQuery> statisticsInfoMapper;

    private final UserInfoMapper <UserInfo, UserInfoQuery> userinfoMapper;

    /**
     * 获取近7天的统计信息
     *
     * @return 所有统计信息
     */
    public List <StatisticsInfoVO> getRecentStatisticsInfo()
    {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate earliestDay = today.minusDays(7);

        // 统计 7天前 ~ 昨天 的数据，共7天
        List <StatisticsInfoVO> records = statisticsInfoMapper.selectVoByStatisticsDatePeriod(earliestDay, yesterday);

        // 按 (date, dataType) 聚合，累加各用户的 statisticsCount
        Map <String, StatisticsInfoVO> aggregated = records.stream()
                                                           .collect(Collectors.toMap(
                                                                   vo -> vo.getStatisticsDate() + "_" + vo.getDataType(),
                                                                   vo -> new StatisticsInfoVO(vo.getStatisticsDate(),
                                                                                              vo.getDataType(),
                                                                                              vo.getStatisticsCount()),
                                                                   (a, b) ->
                                                                   {
                                                                       a.setStatisticsCount(a.getStatisticsCount() + b.getStatisticsCount());
                                                                       return a;
                                                                   }
                                                           ));

        // 补齐缺失的天和类型，statisticsCount 设为 0
        for (LocalDate date = earliestDay ; !date.isAfter(yesterday) ; date = date.plusDays(1))
        {
            for (DataType dataType : DataType.values())
            {
                String key = date + "_" + dataType.getValue();
                aggregated.putIfAbsent(key, new StatisticsInfoVO(date, dataType.getValue(), 0));
            }
        }

        // 不用传FOLLOW数据
        List <StatisticsInfoVO> vos = aggregated.values()
                                                .stream()
                                                .filter(vo -> !vo.getDataType().equals(DataType.FOLLOWER.getValue()))
                                                .sorted(Comparator.comparing(StatisticsInfoVO::getStatisticsDate)
                                                                  .thenComparing(StatisticsInfoVO::getDataType))
                                                .toList();

        return vos;
    }

    /**
     * 获取指定时间范围内的用户量统计数据<hr/>
     *
     * @param startDate 起始时间
     * @param endDate   结束时间
     * @return 数据列表
     */
    public List <UserStatVO> getUserStatByDatePeriod(LocalDate startDate, LocalDate endDate)
    {
        if (startDate.isAfter(endDate))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        List <UserStatVO> vos = userinfoMapper.selectStatByTimePeriod(startDate, endDate);
        Set <LocalDate> dateSet = vos.stream().map(UserStatVO::getDate).collect(Collectors.toSet());

        for (LocalDate date = startDate ; !date.isAfter(endDate) ; date = date.plusDays(1))
        {
            if (!dateSet.contains(date))
            {
                vos.add(new UserStatVO(0, date));
            }
        }

        vos.sort(Comparator.comparing(UserStatVO::getDate));

        return vos;
    }
}
