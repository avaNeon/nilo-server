package com.neon.nilomqconsumer.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Repository
public class StatisticsRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * <b>获取每日播放统计，并进行自定义操作</b><hr/>
     *
     * @param statisticsDate 统计日期
     * @param scanCount      单次扫描最大长度
     * @param batchSize      批处理大小
     * @param batchConsumer  批处理操作
     */
    public void scanDailyPlayCount(LocalDate statisticsDate,
                                   int scanCount,
                                   int batchSize,
                                   Consumer <Map <Long, Integer>> batchConsumer)
    {
        // redis key
        String key = RedisKey.VIDEO_DAILY_PLAY_COUNT_PREFIX + statisticsDate;

        // 设置单次扫描大小
        ScanOptions options = ScanOptions.scanOptions().count(scanCount).build();

        Map <Long, Integer> batch = new HashMap <>(batchSize);

        try (Cursor <Map.Entry <Object, Object>> cursor = redisTemplate.opsForHash().scan(key, options))
        {
            while (cursor.hasNext())
            {
                Map.Entry <Object, Object> entry = cursor.next();

                Long videoId = Long.valueOf(entry.getKey().toString());
                Integer playCount = Integer.valueOf(entry.getValue().toString());

                batch.put(videoId, playCount);

                //  到达指定批大小，处理所有所有数据
                if (batch.size() >= batchSize)
                {
                    batchConsumer.accept(Map.copyOf(batch));
                    batch.clear();
                }
            }

            // 处理剩余数据
            if (!batch.isEmpty())
            {
                batchConsumer.accept(Map.copyOf(batch));
            }
        }
    }
}
