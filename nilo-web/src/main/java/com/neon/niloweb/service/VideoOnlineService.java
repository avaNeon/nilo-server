package com.neon.niloweb.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.neon.niloweb.repository.rabbitmq.VideoOnlineMqRepository;
import com.neon.niloweb.repository.redis.VideoOnlineRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class VideoOnlineService
{
    private final VideoOnlineRedisRepository videoOnlineRedisRepository;

    private final VideoOnlineMqRepository videoOnlineMqRepository;

    // Caffeine 本地缓存
    private final Cache <String, Long> onlineCountCache = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS)    // 5秒过期
                                                                  .maximumSize(10_000)  // 最多缓存1万个视频的在线人数
                                                                  .recordStats()    // 开启性能统计
                                                                  .build();

    /**
     * 发送心跳信息
     *
     * @param videoId   视频ID
     * @param fileIndex 文件序号
     * @param sessionId 会话ID
     */
    public void sendHeartbeat(long videoId, int fileIndex, String sessionId)
    {
        String message = videoId + ":" + fileIndex + ":" + sessionId + ":" + System.currentTimeMillis();
        // 发送给 RabbitMQ
        videoOnlineMqRepository.sendHeartbeat(message);
    }

    /**
     * 获取在线人数
     *
     * @param videoId   视频ID
     * @param fileIndex 文件序号
     * @return 在线人数
     */
    public Long getOnlineCount(long videoId, int fileIndex)
    {
        String videoIdAndFileIndex = videoId + ":" + fileIndex;
        // 先查 Caffeine，如果没有，它会自动执行 getOnlineCountFromRedis 方法去查Redis，并将结果存入 Caffeine
        // 这里的 get 方法自带线程安全的防击穿锁
        return onlineCountCache.get(videoIdAndFileIndex, videoOnlineRedisRepository::getOnlineCountFromRedis);
    }
}
