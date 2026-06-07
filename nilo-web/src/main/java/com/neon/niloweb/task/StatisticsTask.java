package com.neon.niloweb.task;

import com.neon.nilocommon.entity.enums.statisticsInfo.StatisticsTaskType;
import com.neon.niloweb.repository.rabbitmq.StatisticsMqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class StatisticsTask
{
    private final StatisticsMqRepository statisticsMqRepository;

    /**
     * 开始每日统计数据
     */
    @Scheduled(cron = "5 0 0 * * ?")
    public void doDailyStatistics()
    {
        statisticsMqRepository.sendStatisticsProcessMessage(StatisticsTaskType.PULL_VIDEO_PLAY_DAILY);

        statisticsMqRepository.sendStatisticsProcessMessage(StatisticsTaskType.FOLLOWER_STATISTICS);

        statisticsMqRepository.sendStatisticsProcessMessage(StatisticsTaskType.COMMENT_STATISTICS);

        statisticsMqRepository.sendStatisticsProcessMessage(StatisticsTaskType.DANMAKU_STATISTICS);

        statisticsMqRepository.sendStatisticsProcessMessage(StatisticsTaskType.VIDEO_ACTION_STATISTICS);

        statisticsMqRepository.sendStatisticsProcessMessage(StatisticsTaskType.DELETE_EXPIRED_STATISTICS);
    }
}
