package com.neon.nilocommon.entity.dto.mq;

import com.neon.nilocommon.entity.enums.statisticsInfo.StatisticsTaskType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsTaskDTO
{
    private String statisticsId;

    private LocalDateTime statisticsTime;

    private StatisticsTaskType statisticsTaskType;
}
