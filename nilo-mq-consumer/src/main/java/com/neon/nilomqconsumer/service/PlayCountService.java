package com.neon.nilomqconsumer.service;

import com.neon.nilomqconsumer.service.async.PlayCountAsyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
public class PlayCountService
{
    private final PlayCountAsyncService playCountAsyncService;

    public void flushPlayCount(Map <Long, Integer> batch)
    {
        if (batch == null || batch.isEmpty())
        {
            return;
        }

        // ES 播放量由 Canal 同步 MySQL 变更，这里只刷 MySQL + Redis
        CompletableFuture <Void> mysqlCompletableFuture = playCountAsyncService.flushPlayCountBatchToMysql(batch);
        CompletableFuture <Void> redisCompletableFuture = playCountAsyncService.flushPlayCountBatchToRedis(batch);

        CompletableFuture.allOf(mysqlCompletableFuture, redisCompletableFuture).join();
    }
}
