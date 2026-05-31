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
        statisticsRedisRepository.scanDailyPlayCount(statisticsDate, DAILY_PLAY_SCAN_COUNT, DAILY_PLAY_BATCH_SIZE, map ->
        {
            Map <Long, VideoPlayDaily> idVideoMap = videoInfoMapper.selectVideoInfoByVideoIdBatch(new ArrayList <>(map.keySet()))
                                                                   .stream()
                                                                   .collect(Collectors.toMap(VideoInfo::getVideoId,
                                                                                             videoInfo -> new VideoPlayDaily(
                                                                                                     statisticsDate,
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
        return statisticsInfoMapper.reduceVideoPlayDaily(statisticsDate);
    }

    /**
     * 删除一天的统计记录
     *
     * @param localDate 日期
     */
    public Integer deleteVideoPlayDaily(LocalDate localDate)
    {
        return videoPlayDailyMapper.deleteByStatisticsDate(localDate);
    }

    /**
     * 统计用户每日新增粉丝数。
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    public Integer collectDailyFollowerStatistics(LocalDate statisticsDate)
    {
        DateRange dateRange = buildDateRange(statisticsDate);
        return statisticsInfoMapper.reduceDailyFollower(statisticsDate, dateRange.startDate(), dateRange.endDate());
    }

    /**
     * 统计用户每日收到的评论数。
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    public Integer collectDailyCommentStatistics(LocalDate statisticsDate)
    {
        DateRange dateRange = buildDateRange(statisticsDate);
        return statisticsInfoMapper.reduceDailyComment(statisticsDate, dateRange.startDate(), dateRange.endDate());
    }

    /**
     * 统计用户每日收到的弹幕数。
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    public Integer collectDailyDanmakuStatistics(LocalDate statisticsDate)
    {
        DateRange dateRange = buildDateRange(statisticsDate);
        return statisticsInfoMapper.reduceDailyDanmaku(statisticsDate, dateRange.startDate(), dateRange.endDate());
    }

    /**
     * 统计用户每日收到的点赞、收藏、投币数。
     *
     * @param statisticsDate 统计日期
     * @return 改变行数
     */
    public Integer collectDailyVideoActionStatistics(LocalDate statisticsDate)
    {
        DateRange dateRange = buildDateRange(statisticsDate);
        return statisticsInfoMapper.reduceDailyVideoAction(statisticsDate, dateRange.startDate(), dateRange.endDate());
    }

    private DateRange buildDateRange(LocalDate statisticsDate)
    {
        return new DateRange(statisticsDate.atStartOfDay(), statisticsDate.plusDays(1).atStartOfDay());
    }

    private record DateRange(LocalDateTime startDate, LocalDateTime endDate)
    {
    }
}
