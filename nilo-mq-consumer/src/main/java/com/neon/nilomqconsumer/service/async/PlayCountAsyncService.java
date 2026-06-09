package com.neon.nilomqconsumer.service.async;

import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilomqconsumer.mapper.VideoInfoMapper;
import com.neon.nilomqconsumer.repository.elasticsearch.VideoInfoDocRepository;
import com.neon.nilomqconsumer.repository.redis.HotVideoRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlayCountAsyncService
{
    private static final int MYSQL_PLAY_COUNT_BATCH_SIZE = 100;

    private static final int ES_PLAY_COUNT_BATCH_SIZE = 200;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final HotVideoRedisRepository hotVideoRedisRepository;

    private final VideoInfoDocRepository videoInfoDocRepository;

    @Async("playCountExecutor")
    public CompletableFuture <Void> flushPlayCountBatchToMysql(Map <Long, Integer> batch)
    {
        if (batch == null || batch.isEmpty())
        {
            return CompletableFuture.completedFuture(null);
        }

        int failCount = 0;
        Map <Long, Integer> subBatch = new LinkedHashMap <>(MYSQL_PLAY_COUNT_BATCH_SIZE);

        // 采用折中方案，100条记录整合为一条SQL让MySQL处理，如果失败100条记录全部失效
        for (Map.Entry <Long, Integer> entry : batch.entrySet())
        {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0)
            {
                continue;
            }

            subBatch.put(entry.getKey(), entry.getValue());

            if (subBatch.size() >= MYSQL_PLAY_COUNT_BATCH_SIZE)
            {
                failCount += flushMysqlPlayCountSubBatch(subBatch);
                subBatch.clear();
            }
        }

        if (!subBatch.isEmpty())
        {
            failCount += flushMysqlPlayCountSubBatch(subBatch);
        }

        if (failCount > 0)
        {
            log.warn("MySQL批量刷新播放量部分失败，失败批次数：{}，本批次不触发MQ重试", failCount);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Async("playCountExecutor")
    public CompletableFuture <Void> flushPlayCountBatchToRedis(Map <Long, Integer> batch)
    {
        hotVideoRedisRepository.updateVideoPlayCountBatch(batch);
        return CompletableFuture.completedFuture(null);
    }

    @Async("playCountExecutor")
    public CompletableFuture <Void> flushPlayCountToES(Map <Long, Integer> batch)
    {
        if (batch == null || batch.isEmpty())
        {
            return CompletableFuture.completedFuture(null);
        }

        int failCount = 0;
        Map <Long, Integer> subBatch = new LinkedHashMap <>(ES_PLAY_COUNT_BATCH_SIZE);

        for (Map.Entry <Long, Integer> entry : batch.entrySet())
        {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0)
            {
                continue;
            }

            subBatch.put(entry.getKey(), entry.getValue());

            if (subBatch.size() >= ES_PLAY_COUNT_BATCH_SIZE)
            {
                failCount += flushEsPlayCountSubBatch(subBatch);
                subBatch.clear();
            }
        }

        if (!subBatch.isEmpty())
        {
            failCount += flushEsPlayCountSubBatch(subBatch);
        }

        if (failCount > 0)
        {
            log.warn("ES批量刷新播放量部分失败，失败数量：{}，本批次不触发MQ重试", failCount);
        }

        return CompletableFuture.completedFuture(null);
    }

    private int flushMysqlPlayCountSubBatch(Map <Long, Integer> subBatch)
    {
        try
        {
            Integer affectedRows = videoInfoMapper.increasePlayCountBatch(subBatch);
            if (affectedRows == null || affectedRows < subBatch.size())
            {
                log.warn("MySQL播放量小批次刷新影响行数少于预期，expected={}, actual={}", subBatch.size(), affectedRows);
            }
            return 0;
        }
        catch (Exception e)
        {
            log.warn("MySQL播放量小批次刷新失败，batchSize={}，跳过该小批次", subBatch.size(), e);
            return 1;
        }
    }

    private int flushEsPlayCountSubBatch(Map <Long, Integer> subBatch)
    {
        try
        {
            return videoInfoDocRepository.increasePlayCountByVideoId(subBatch);
        }
        catch (Exception e)
        {
            log.warn("ES播放量小批次刷新失败，batchSize={}，跳过该小批次", subBatch.size(), e);
            return subBatch.size();
        }
    }
}
