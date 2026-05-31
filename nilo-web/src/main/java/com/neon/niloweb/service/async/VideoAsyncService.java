package com.neon.niloweb.service.async;

import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.niloweb.mapper.VideoInfoMapper;
import com.neon.niloweb.repository.elasticsearch.VideoInfoDocRepository;
import com.neon.niloweb.repository.redis.HotVideoRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoAsyncService
{
    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final HotVideoRedisRepository hotVideoRedisRepository;

    private final VideoInfoDocRepository videoInfoDocRepository;

    /**
     * 异步批量更新MySQL视频播放数<hr/>
     *
     * @param batch 一批 &lt;videoId,播放量&gt; 的Map
     */
    @Async("playCountExecutor")
    public CompletableFuture <Void> flushPlayCountBatchToMysql(Map <Long, Integer> batch)
    {
        int failCount = 0;

        // 批量更新mysql播放量，一个更新失败不用管
        for (Map.Entry <Long, Integer> entry : batch.entrySet())
        {
            try
            {
                videoInfoMapper.increaseByField(entry.getKey(), "play_count", entry.getValue());
            }
            catch (Exception e)
            {
                failCount++;
                log.warn("MySQL中{}视频增加播放量{}失败", entry.getKey(), entry.getValue(), e);
            }
        }

        if (failCount > 0)
        {
            throw new RuntimeException("MySQL批量刷新播放量部分失败，失败数量：" + failCount);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 异步批量更新Redis活跃视频播放数<hr/>
     *
     * @param batch 一批 &lt;videoId,播放量&gt; 的Map
     */
    @Async("playCountExecutor")
    public CompletableFuture <Void> flushPlayCountBatchToRedis(Map <Long, Integer> batch)
    {
        try
        {
            hotVideoRedisRepository.updateVideoPlayCountBatch(batch);
        }
        catch (Exception e)
        {
            log.warn("批量异步刷新redis播放统计数据时失败！", e);
            throw e;
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 异步批量更新ES视频播放数<hr/>
     *
     * @param batch 一批 &lt;videoId,播放量&gt; 的Map
     */
    @Async("playCountExecutor")
    public CompletableFuture <Void> flushPlayCountToES(Map <Long, Integer> batch)
    {
        int failCount = 0;

        for (Map.Entry <Long, Integer> entry : batch.entrySet())
        {
            try
            {
                videoInfoDocRepository.increasePlayCountByVideoId(entry.getKey(), entry.getValue());
            }
            catch (Exception e)
            {
                failCount++;
                log.warn("ES中{}视频增加播放量{}失败", entry.getKey(), entry.getValue(), e);
            }
        }

        if (failCount > 0)
        {
            throw new RuntimeException("ES批量刷新播放量部分失败，失败数量：" + failCount);
        }

        return CompletableFuture.completedFuture(null);
    }
}
