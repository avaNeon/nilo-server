package com.neon.niloweb;

import com.neon.nilocommon.entity.enums.statisticsInfo.DataType;
import com.neon.nilocommon.entity.po.StatisticsInfo;
import com.neon.nilocommon.entity.query.StatisticsInfoQuery;
import com.neon.niloweb.mapper.StatisticsInfoMapper;
import com.neon.niloweb.task.StatisticsTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class DoDailyStatisticsTest
{
    private static final Long TEST_USER_ID = 1L;

    @Autowired
    private StatisticsTask statisticsTask;

    @Autowired
    private StatisticsInfoMapper<StatisticsInfo, StatisticsInfoQuery> statisticsInfoMapper;

    @Test
    public void test()
    {
        statisticsTask.doDailyStatistics();
    }

    @Test
    public void generateStatisticsInfoTestData()
    {
        LocalDate startDate = LocalDate.of(2026, 5, 23);
        LocalDate endDate = LocalDate.of(2026, 5, 29);
        List<StatisticsInfo> statisticsInfoList = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1))
        {
            int dayOffset = date.getDayOfMonth() - startDate.getDayOfMonth();
            for (DataType dataType : DataType.values())
            {
                StatisticsInfo statisticsInfo = new StatisticsInfo();
                statisticsInfo.setStatisticsDate(date);
                statisticsInfo.setUserId(TEST_USER_ID);
                statisticsInfo.setDataType(dataType.getValue());
                statisticsInfo.setStatisticsCount((dayOffset + 1) * 100 + dataType.getValue());
                statisticsInfoList.add(statisticsInfo);
            }
        }

        statisticsInfoMapper.insertOrUpdateBatch(statisticsInfoList);
    }

}
