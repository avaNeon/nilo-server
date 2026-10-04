package com.neon.nilomqconsumer.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class HotVideoRedisRepository
{
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 单个视频播放量写入的 Lua 脚本<hr/>
     * 视频在热门榜就累加热门计数，在冷门榜就累加冷门计数，都不在就先加入冷门榜；同时累加当日播放量
     */
    private static final DefaultRedisScript <Long> UPDATE_VIDEO_PLAY_COUNT_SCRIPT = new DefaultRedisScript <>("""
            local hot_ranking_key = KEYS[1]
            local hot_counting_key_prefix = KEYS[2]
            local cold_ranking_key = KEYS[3]
            local cold_counting_key_prefix = KEYS[4]
            local video_record_prefix = KEYS[5]

            local video_id = ARGV[1]
            local increment = tonumber(ARGV[2])
            local currentHour = tonumber(ARGV[3])
            local currentDay = ARGV[4]

            local hotRank = redis.call('ZRANK', hot_ranking_key, video_id)
            local coldRank = redis.call('ZRANK', cold_ranking_key, video_id)

            if hotRank then
                redis.call('ZINCRBY', hot_counting_key_prefix .. video_id, increment, currentHour)
            elseif coldRank then
                redis.call('ZINCRBY', cold_counting_key_prefix .. video_id, increment, currentHour)
            else
                redis.call('ZADD', cold_ranking_key, increment, video_id)
                redis.call('ZINCRBY', cold_counting_key_prefix .. video_id, increment, currentHour)
            end

            local video_record_key = video_record_prefix .. currentDay
            local exist = redis.call('EXISTS', video_record_key) == 1
            redis.call('HINCRBY', video_record_key, video_id, increment)

            if not exist then
                redis.call('EXPIRE', video_record_key, 172800)
            end

            return 1
            """, Long.class);

    /**
     * 更新单个视频的播放量：热度计数和当日播放量
     *
     * @param videoId   视频ID
     * @param increment 播放量增量
     */
    public void updateVideoPlayCount(long videoId, int increment)
    {
        long currentHour = ChronoUnit.HOURS.between(Instant.EPOCH, Instant.now());
        String currentDay = LocalDate.now().toString();

        stringRedisTemplate.execute(UPDATE_VIDEO_PLAY_COUNT_SCRIPT,
                                    List.of(RedisKey.HOT_VIDEO_RANKING,
                                            RedisKey.HOT_VIDEO_COUNTING_PREFIX,
                                            RedisKey.COLD_VIDEO_RANKING,
                                            RedisKey.COLD_VIDEO_COUNTING_PREFIX,
                                            RedisKey.VIDEO_DAILY_PLAY_COUNT_PREFIX),
                                    String.valueOf(videoId),
                                    String.valueOf(increment),
                                    String.valueOf(currentHour),
                                    currentDay);
    }
}
