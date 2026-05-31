package com.neon.nilocommon.entity.query;

import lombok.Getter;
import lombok.Setter;


/**
 * 参数
 */
@Setter
@Getter
public class VideoPlayDailyQuery extends BaseQuery
{

    private String statisticsDate;

    private String statisticsDateStart;

    private String statisticsDateEnd;

    private Long videoId;

    private Long videoUserId;

    private Integer playCount;
}
