package com.neon.niloweb.task;

import com.neon.niloweb.repository.redis.HotVideoRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class HotVideoTask
{
    private final HotVideoRedisRepository hotVideoRedisRepository;

    /**
     * 定时刷新冷表播放统计，并按阈值将视频晋级到热表
     */
    @Scheduled(fixedRateString = "#{@webConfig.coldKeyUpdateInterval * 1000}")
    public void refreshColdCount()
    {
        hotVideoRedisRepository.refreshColdCount();
    }

    /**
     * 定时刷新热表播放统计，并按阈值将视频降级到冷表
     */
    @Scheduled(fixedRateString = "#{@webConfig.hotKeyUpdateInterval * 1000}")
    public void refreshHotCount()
    {
        hotVideoRedisRepository.refreshHotCount();
    }
}
