package com.neon.nilocommon.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class TimeUtil
{
    /**
     * 获取当前时间到明日零点的秒数
     */
    public static long getSecondsUntilTomorrow()
    {
        LocalDateTime now = LocalDateTime.now();

        return getSecondsUntilTomorrow(now);
    }

    /**
     * 获取指定时间到明日零点的秒数
     */
    public static long getSecondsUntilTomorrow(LocalDateTime dateTime)
    {
        LocalDateTime tomorrow = dateTime.toLocalDate().plusDays(1).atStartOfDay();

        // 至少有1s
        return Math.max(1, ChronoUnit.SECONDS.between(dateTime, tomorrow));
    }
}
