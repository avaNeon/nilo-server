package com.neon.nilomqconsumer.service;

import com.neon.nilocommon.entity.po.StatisticsInfo;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.po.VideoPlayDaily;
import com.neon.nilocommon.entity.query.StatisticsInfoQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.query.VideoPlayDailyQuery;
import com.neon.nilomqconsumer.mapper.StatisticsInfoMapper;
import com.neon.nilomqconsumer.mapper.VideoInfoMapper;
import com.neon.nilomqconsumer.mapper.VideoPlayDailyMapper;
import com.neon.nilomqconsumer.repository.redis.StatisticsRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class StatisticsService
{
    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoPlayDailyMapper <VideoPlayDaily, VideoPlayDailyQuery> videoPlayDailyMapper;

    private final StatisticsInfoMapper <StatisticsInfo, StatisticsInfoQuery> statisticsInfoMapper;

    private final StatisticsRedisRepository statisticsRedisRepository;

    private static final Integer DAILY_PLAY_SCAN_COUNT = 1000;

    private static final Integer DAILY_PLAY_BATCH_SIZE = 2000;

    /**
     * 将Redis中的每日播放记录分批保存至MySQL视频日播放明细表
     *
     * @param statisticsDate 统计日期
     */
    public void collectVideoPlayDailyStatistics(LocalDate statisticsDate)
    {
        LocalDate targetStatisticsDate = buildTargetStatisticsDate(statisticsDate);

        statisticsRedisRepository.scanDailyPlayCount(targetStatisticsDate, DAILY_PLAY_SCAN_COUNT, DAILY_PLAY_BATCH_SIZE, map ->
        {
            Map <Long, VideoPlayDaily> idVideoMap = videoInfoMapper.selectVideoInfoByVideoIdBatch(new ArrayList <>(map.keySet()))
                                                                   .stream()
                                                                   .collect(Collectors.toMap(VideoInfo::getVideoId,
                                                                                             videoInfo -> new VideoPlayDaily(
                                                                                                     targetStatisticsDate,
                                                                                                     videoInfo.getVideoId(),
                                                                                                     videoInfo.getUserId(),
                                                                                                     0)));

            // Redis中可能残留已删除视频的播放记录，找不到视频信息时跳过。
            for (Map.Entry <Long, Integer> entry : map.entrySet())
            {
                VideoPlayDaily videoPlayDaily = idVideoMap.get(entry.getKey());
                if (videoPlayDaily != null)
                {
                    videoPlayDaily.setPlayCount(entry.getValue());
                }
            }

            List <VideoPlayDaily> videoPlayDailyList = idVideoMap.values().stream().toList();
            if (!videoPlayDailyList.isEmpty())
            {
                videoPlayDailyMapper.insertOrUpdateBatch(videoPlayDailyList);
            }
        });
    }

    /**
     * 统计用户每日播放量<hr/>
     * <p>合并单个视频的每日播放量，计算出每个用户的每日播放量</p>
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    public Integer collectUserPlayStatistics(LocalDate statisticsDate)
    {
        return statisticsInfoMapper.reduceVideoPlayDaily(buildTargetStatisticsDate(statisticsDate));
    }

    /**
     * 删除一天的统计记录
     *
     * @param localDate 日期
     */
    public Integer deleteVideoPlayDaily(LocalDate localDate)
    {
        return videoPlayDailyMapper.deleteByStatisticsDate(buildTargetStatisticsDate(localDate));
    }

    /**
     * 统计用户每日新增粉丝数。
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    public Integer collectDailyFollowerStatistics(LocalDate statisticsDate)
    {
        LocalDate targetStatisticsDate = buildTargetStatisticsDate(statisticsDate);
        DateRange dateRange = buildDateRange(targetStatisticsDate);
        return statisticsInfoMapper.reduceDailyFollower(targetStatisticsDate, dateRange.startDate(), dateRange.endDate());
    }

    /**
     * 统计用户每日收到的评论数。
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    public Integer collectDailyCommentStatistics(LocalDate statisticsDate)
    {
        LocalDate targetStatisticsDate = buildTargetStatisticsDate(statisticsDate);
        DateRange dateRange = buildDateRange(targetStatisticsDate);
        return statisticsInfoMapper.reduceDailyComment(targetStatisticsDate, dateRange.startDate(), dateRange.endDate());
    }

    /**
     * 统计用户每日收到的弹幕数。
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    public Integer collectDailyDanmakuStatistics(LocalDate statisticsDate)
    {
        LocalDate targetStatisticsDate = buildTargetStatisticsDate(statisticsDate);
        DateRange dateRange = buildDateRange(targetStatisticsDate);
        return statisticsInfoMapper.reduceDailyDanmaku(targetStatisticsDate, dateRange.startDate(), dateRange.endDate());
    }

    /**
     * 统计用户每日收到的点赞、收藏、投币数。
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    public Integer collectDailyVideoActionStatistics(LocalDate statisticsDate)
    {
        LocalDate targetStatisticsDate = buildTargetStatisticsDate(statisticsDate);
        DateRange dateRange = buildDateRange(targetStatisticsDate);
        return statisticsInfoMapper.reduceDailyVideoAction(targetStatisticsDate, dateRange.startDate(), dateRange.endDate());
    }

    /**
     * 删除过期数据
     *
     * @param statisticsDate 统计日期
     * @return 删除行数
     */
    public Integer deleteExpiredStatistics(LocalDate statisticsDate)
    {
        return statisticsInfoMapper.deleteStatisticsBeforeDate(statisticsDate.minusDays(7));
    }

    private DateRange buildDateRange(LocalDate statisticsDate)
    {
        return new DateRange(statisticsDate.atStartOfDay(), statisticsDate.plusDays(1).atStartOfDay());
    }

    /**
     * <b>将统计日期转化为我们真正要统计的日期</b>
     * @param statisticsDate 统计日期
     * @return 转化后的日期
     */
    private LocalDate buildTargetStatisticsDate(LocalDate statisticsDate)
    {
        return statisticsDate.minusDays(1);
    }

    private record DateRange(LocalDateTime startDate, LocalDateTime endDate)
    {
    }
}
