package com.neon.niloweb.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.neon.niloweb.repository.rabbitmq.VideoOnlineMqRepository;
import com.neon.niloweb.repository.redis.VideoOnlineRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class VideoOnlineService
{
    private final VideoOnlineRedisRepository videoOnlineRedisRepository;

    private final VideoOnlineMqRepository videoOnlineMqRepository;

    /**
     * Caffeine 本地缓存
     */
    private final Cache <String, Long> onlineCountCache = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS)    // 5秒过期
                                                                  .maximumSize(10_000)  // 最多缓存1万个视频的在线人数
                                                                  .recordStats()    // 开启性能统计
                                                                  .build();
    /**
     * 本地缓存一次批量发送心跳条数
     */
    private final Integer batchSize = 1000;

    /**
     * <b>本地心跳信息缓存</b><hr/>
     * <p>用于批量暂存一定时间窗口内的心跳消息，然后固定时间发送给MQ，大幅减少网络传输消耗</p>
     * <p>key-value: videoId:fileIndex:userId - timestamp</p>
     */
    private final ConcurrentHashMap <String, Long> heartbeatBuffer = new ConcurrentHashMap <>();

    /**
     * 发送心跳信息
     *
     * @param videoId   视频ID
     * @param fileIndex 文件序号
     * @param sessionId 会话ID
     */
    public void sendHeartbeat(long videoId, int fileIndex, String sessionId)
    {
        String keyPrefix = videoId + ":" + fileIndex + ":" + sessionId;
        // 暂存于本地缓存
        heartbeatBuffer.put(keyPrefix, System.currentTimeMillis());
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

    /**
     * 定时将本地缓存心跳发送给MQ
     */
    @Scheduled(fixedRateString = "#{@systemConfig.heartbeatSendInterval}")
    private void flushHeartbeatBuffer()
    {
        // 如果缓存没有数据，不用发送
        if (heartbeatBuffer.isEmpty())
        {
            return;
        }

        // 准备发送给MQ的消息列表
        ArrayList <String> messageList = new ArrayList <>();

        for (Map.Entry <String, Long> entry : heartbeatBuffer.entrySet())
        {
            String keyPrefix = entry.getKey();
            Long timestamp = entry.getValue();

            // 在移除时确认时间戳是否与去除时相同，如果不同则说明数据在此期间被改变，本次不传入给MQ
            boolean isRemoved = heartbeatBuffer.remove(keyPrefix, timestamp);

            // 如果成功移除，填入消息列表
            if (isRemoved)
            {
                messageList.add(keyPrefix + ":" + timestamp);
            }
        }

        // 如果有消息可发，则发送给MQ
        if (!messageList.isEmpty())
        {
            int size = messageList.size();

            // 每次最多发送固定条数，在java端做好削峰
            for (int start = 0 ; start < size ; start += batchSize)
            {
                int end = Math.min(start + batchSize, size);
                videoOnlineMqRepository.sendHeartbeat(messageList.subList(start, end));
            }
        }
    }
}
