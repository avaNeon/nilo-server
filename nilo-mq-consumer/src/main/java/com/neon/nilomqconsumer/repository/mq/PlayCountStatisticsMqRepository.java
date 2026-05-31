package com.neon.nilomqconsumer.repository.mq;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.mq.StatisticsTaskDTO;
import com.neon.nilocommon.entity.enums.statisticsInfo.StatisticsTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Repository
public class PlayCountStatisticsMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送合并每日播放统计数据的消息
     *
     * @param statisticsTime 统计时间
     */
    public void sendUserPlayDailyStatistics(LocalDateTime statisticsTime)
    {
        // 组装
        StatisticsTaskType type = StatisticsTaskType.REDUCE_PLAY_DAILY_STATISTICS;
        StatisticsTaskDTO dto = new StatisticsTaskDTO(type.getLabel() + ":" + statisticsTime, statisticsTime, type);

        // 发送
        rabbitTemplate.convertAndSend(MqInfo.STATISTIC_EXCHANGE, MqInfo.STATISTIC_ROUTING_KEY, dto);
    }

    /**
     * 发送删除MySQL中视频每日播放统计
     *
     * @param statisticsTime 统计时间
     */
    public void sendDeleteVideoPlayDailyStatistics(LocalDateTime statisticsTime)
    {
        // 组装
        StatisticsTaskType type = StatisticsTaskType.DELETE_VIDEO_PLAY_DAILY;
        StatisticsTaskDTO dto = new StatisticsTaskDTO(type.getLabel() + ":" + statisticsTime, statisticsTime, type);

        // 发送
        rabbitTemplate.convertAndSend(MqInfo.STATISTIC_EXCHANGE, MqInfo.STATISTIC_ROUTING_KEY, dto);
    }
}
