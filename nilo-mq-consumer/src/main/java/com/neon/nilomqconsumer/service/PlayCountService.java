package com.neon.nilomqconsumer.service;

import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilomqconsumer.mapper.VideoInfoMapper;
import com.neon.nilomqconsumer.repository.redis.HotVideoRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlayCountService
{
    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final HotVideoRedisRepository hotVideoRedisRepository;

    /**
     * 逐个视频刷新播放量<hr/>
     * 每个视频先执行一条 UPDATE 写 MySQL，再调用一次 Redis
     *
     * @param batch key 为视频ID，value 为播放量增量
     */
    public void flushPlayCount(Map <Long, Integer> batch)
    {
        if (batch == null || batch.isEmpty())
        {
            return;
        }

        // ES 播放量由 Canal 同步 MySQL 变更，这里只刷 MySQL + Redis
        for (Map.Entry <Long, Integer> entry : batch.entrySet())
        {
            Long videoId = entry.getKey();
            Integer increment = entry.getValue();
            if (videoId == null || increment == null || increment <= 0)
            {
                continue;
            }

            // MySQL 写失败只跳过这个视频，不触发 MQ 重试
            try
            {
                Integer affectedRows = videoInfoMapper.increaseByField(videoId, "play_count", increment);
                if (affectedRows == null || affectedRows < 1)
                {
                    log.warn("MySQL播放量刷新影响行数为0，videoId={}", videoId);
                }
            }
            catch (Exception e)
            {
                log.warn("MySQL播放量刷新失败，videoId={}，跳过该视频", videoId, e);
            }

            hotVideoRedisRepository.updateVideoPlayCount(videoId, increment);
        }
    }
}
