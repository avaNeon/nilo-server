package com.neon.niloweb.service;

import com.neon.nilocommon.entity.enums.statisticsInfo.DataType;
import com.neon.nilocommon.entity.po.StatisticsInfo;
import com.neon.nilocommon.entity.query.StatisticsInfoQuery;
import com.neon.nilocommon.entity.vo.StatisticsInfoVO;
import com.neon.niloweb.mapper.StatisticsInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class StatisticsService
{
    private final StatisticsInfoMapper <StatisticsInfo, StatisticsInfoQuery> statisticsInfoMapper;

    /**
     * 获取近7天的统计信息
     *
     * @param userId 用户ID
     * @return 所有统计信息
     */
    public List <StatisticsInfoVO> getRecentStatisticsInfo(long userId)
    {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate earliestDay = today.minusDays(7);

        // 统计 7天前 ~ 昨天 的数据，共7天
        List <StatisticsInfoVO> records = statisticsInfoMapper.selectVoByUserIdAndStatisticsDatePeriod(userId,
                                                                                                       earliestDay,
                                                                                                       yesterday);

        // 将查到的记录按 (date, dataType) 建索引
        Set <String> existingKeys = records.stream()
                                           .map(vo -> vo.getStatisticsDate() + "_" + vo.getDataType())
                                           .collect(Collectors.toSet());

        // 补齐缺失的天和类型，statisticsCount 设为 0
        List <StatisticsInfoVO> result = new ArrayList <>(records);
        for (LocalDate date = earliestDay ; !date.isAfter(yesterday) ; date = date.plusDays(1))
        {
            for (DataType dataType : DataType.values())
            {
                String key = date + "_" + dataType.getValue();
                if (!existingKeys.contains(key))
                {
                    result.add(new StatisticsInfoVO(date, dataType.getValue(), 0));
                }
            }
        }

        return result;
    }
}
