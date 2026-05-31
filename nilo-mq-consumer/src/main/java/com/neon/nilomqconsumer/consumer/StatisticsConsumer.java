package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.dto.mq.StatisticsTaskDTO;
import com.neon.nilocommon.entity.enums.statisticsInfo.StatisticsTaskType;
import com.neon.nilomqconsumer.repository.mq.PlayCountStatisticsMqRepository;
import com.neon.nilomqconsumer.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.neon.nilocommon.entity.constants.MqInfo.STATISTIC_QUEUE;

@RequiredArgsConstructor
@Component
public class StatisticsConsumer
{
    private final StatisticsService statisticsService;

    private final PlayCountStatisticsMqRepository playCountStatisticsMqRepository;

    /**
     * <b>统计任务消费者</b><hr/>
     * <p>所有统计任务使用同一个队列，按任务类型分发到具体处理方法。</p>
     *
     * @param dto 消息
     */
    @RabbitListener(queues = STATISTIC_QUEUE, concurrency = "2")
    public void consume(StatisticsTaskDTO dto)
    {
        StatisticsTaskType type = dto.getStatisticsTaskType();

        if (type == null)
        {
            throw new IllegalArgumentException("Statistics task type is null");
        }

        switch (type)
        {
            case PULL_VIDEO_PLAY_DAILY -> pullRedisPlayCountToMysql(dto);
            case REDUCE_PLAY_DAILY_STATISTICS -> reducePlayCountDaily(dto);
            case DELETE_VIDEO_PLAY_DAILY -> deleteVideoPlayDaily(dto);
            case FOLLOWER_STATISTICS -> reduceDailyFollower(dto);
            case COMMENT_STATISTICS -> reduceDailyComment(dto);
            case DANMAKU_STATISTICS -> reduceDailyDanmaku(dto);
            case VIDEO_ACTION_STATISTICS -> reduceDailyVideoAction(dto);
            default -> throw new IllegalArgumentException("Unsupported statistics task type: " + type);
        }
    }

    /**
     * <b>视频播放量统计消费者</b><hr/>
     * <p>将redis中的视频播放量数据拉取并保存到mysql中</p>
     *
     * @param dto 消息
     */
    private void pullRedisPlayCountToMysql(StatisticsTaskDTO dto)
    {
        StatisticsTaskType type = dto.getStatisticsTaskType();

        // 如果类型不一致，直接报错
        if (type != StatisticsTaskType.PULL_VIDEO_PLAY_DAILY)
        {
            throw new IllegalArgumentException("Unsupported statistics task type: " + type);
        }

        LocalDateTime statisticsTime = dto.getStatisticsTime();
        LocalDate statisticsDate = statisticsTime.toLocalDate();

        // 处理
        statisticsService.collectVideoPlayDailyStatistics(statisticsDate);

        // 发送消息，进行下一步操作，合并视频每日播放量数据，保存为用户每日播放量数据
        playCountStatisticsMqRepository.sendUserPlayDailyStatistics(statisticsTime);
    }

    /**
     * <b>合并视频每日播放量数据为用户每日播放量数据</b><hr/>
     *
     * @param dto 消息
     */
    private void reducePlayCountDaily(StatisticsTaskDTO dto)
    {
        StatisticsTaskType type = dto.getStatisticsTaskType();

        // 如果类型不一致，直接报错
        if (type != StatisticsTaskType.REDUCE_PLAY_DAILY_STATISTICS)
        {
            throw new IllegalArgumentException("Unsupported statistics task type: " + type);
        }

        LocalDateTime statisticsTime = dto.getStatisticsTime();
        LocalDate statisticsDate = statisticsTime.toLocalDate();

        // 处理
        statisticsService.collectUserPlayStatistics(statisticsDate);

        // 发送消息，进行下一步操作，将MySQL中视频的每日播放量数据删除
        playCountStatisticsMqRepository.sendDeleteVideoPlayDailyStatistics(statisticsTime);
    }

    /**
     * <b>删除MySQL中视频每日播放记录</b>
     *
     * @param dto 消息
     */
    private void deleteVideoPlayDaily(StatisticsTaskDTO dto)
    {
        StatisticsTaskType type = dto.getStatisticsTaskType();

        // 如果类型不一致，直接报错
        if (type != StatisticsTaskType.DELETE_VIDEO_PLAY_DAILY)
        {
            throw new IllegalArgumentException("Unsupported statistics task type: " + type);
        }

        LocalDate statisticsDate = dto.getStatisticsTime().toLocalDate();

        // 处理
        statisticsService.deleteVideoPlayDaily(statisticsDate);
    }

    /**
     * <b>新增粉丝统计消费者</b><hr/>
     *
     * @param dto 消息
     */
    private void reduceDailyFollower(StatisticsTaskDTO dto)
    {
        StatisticsTaskType type = dto.getStatisticsTaskType();
        if (type != StatisticsTaskType.FOLLOWER_STATISTICS)
        {
            throw new IllegalArgumentException("Unsupported statistics task type: " + type);
        }

        LocalDate statisticsDate = dto.getStatisticsTime().toLocalDate();
        statisticsService.collectDailyFollowerStatistics(statisticsDate);
    }

    /**
     * <b>评论统计消费者</b><hr/>
     *
     * @param dto 消息
     */
    private void reduceDailyComment(StatisticsTaskDTO dto)
    {
        StatisticsTaskType type = dto.getStatisticsTaskType();
        if (type != StatisticsTaskType.COMMENT_STATISTICS)
        {
            throw new IllegalArgumentException("Unsupported statistics task type: " + type);
        }

        LocalDate statisticsDate = dto.getStatisticsTime().toLocalDate();
        statisticsService.collectDailyCommentStatistics(statisticsDate);
    }

    /**
     * <b>弹幕统计消费者</b><hr/>
     *
     * @param dto 消息
     */
    private void reduceDailyDanmaku(StatisticsTaskDTO dto)
    {
        StatisticsTaskType type = dto.getStatisticsTaskType();
        if (type != StatisticsTaskType.DANMAKU_STATISTICS)
        {
            throw new IllegalArgumentException("Unsupported statistics task type: " + type);
        }

        LocalDate statisticsDate = dto.getStatisticsTime().toLocalDate();
        statisticsService.collectDailyDanmakuStatistics(statisticsDate);
    }

    /**
     * <b>视频操作统计消费者</b><hr/>
     * <p>统计点赞、收藏、投币数据。</p>
     *
     * @param dto 消息
     */
    private void reduceDailyVideoAction(StatisticsTaskDTO dto)
    {
        StatisticsTaskType type = dto.getStatisticsTaskType();
        if (type != StatisticsTaskType.VIDEO_ACTION_STATISTICS)
        {
            throw new IllegalArgumentException("Unsupported statistics task type: " + type);
        }

        LocalDate statisticsDate = dto.getStatisticsTime().toLocalDate();
        statisticsService.collectDailyVideoActionStatistics(statisticsDate);
    }
}
