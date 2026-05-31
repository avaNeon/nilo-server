package com.neon.nilocommon.entity.po;

import lombok.*;

import java.time.LocalDate;
import java.util.Date;


/**
 *
 */
@Setter
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class VideoPlayDaily
{
    private LocalDate statisticsDate;

    private Long videoId;

    private Long videoUserId;

    private Integer playCount;
}
