package com.neon.niloweb.repository.rabbitmq;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.mq.StatisticsTaskDTO;
import com.neon.nilocommon.entity.enums.statisticsInfo.StatisticsTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Repository
public class StatisticsMqRepository
{
    private final RabbitTemplate rabbitTemplate;

    /**
     * <b>发送处理统计的信息</b>
     *
     * @param type 处理的类型
     */
    public void sendStatisticsProcessMessage(StatisticsTaskType type)
    {
        // 先校验一下提供的类型是否为空
        if (type == null)
        {
            throw new NullPointerException(
                    "com.neon.niloweb.repository.rabbitmq.StatisticsMqRepository.sendStatisticsProcessMessage: StatisticsTaskType is null");
        }

        LocalDateTime now = LocalDateTime.now();

        StatisticsTaskDTO dto = new StatisticsTaskDTO(type.getLabel() + ":" + now, now, type);

        // 给MQ发消息
        rabbitTemplate.convertAndSend(MqInfo.STATISTIC_EXCHANGE, MqInfo.STATISTIC_ROUTING_KEY, dto);
    }
}
