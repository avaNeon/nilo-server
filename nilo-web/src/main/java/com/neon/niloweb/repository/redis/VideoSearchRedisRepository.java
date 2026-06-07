package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Repository
public class VideoSearchRedisRepository
{
    private static final DateTimeFormatter HOT_KEYWORD_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 增加热词搜索次数
     *
     * @param hotKeyword 热词
     */
    public void addHotKeywordCount(String hotKeyword)
    {
        LocalDate now = LocalDate.now();

        String key = getHotKeywordRankingKey(now);

        redisTemplate.opsForZSet().incrementScore(key, hotKeyword, 1);

        redisTemplate.expireAt(key, getHotKeywordExpireAt(now));
    }

    /**
     * 获取热词榜前若干条记录
     *
     * @param range 查询条数
     * @return 前若干条热词，热度从高到低排列
     */
    public List <String> getHotKeywordRanking(Integer range)
    {
        if (range == null || range <= 0)
        {
            return List.of();
        }

        Set <Object> result = redisTemplate.opsForZSet().reverseRange(getHotKeywordRankingKey(), 0, range - 1);
        if (result == null || result.isEmpty())
        {
            return List.of();
        }
        else
        {
            return result.stream().map(Object::toString).toList();
        }
    }

    private String getHotKeywordRankingKey()
    {
        return RedisKey.HOT_KEYWORD_RANKING + ":" + LocalDate.now().format(HOT_KEYWORD_DATE_FORMATTER);
    }

    private String getHotKeywordRankingKey(LocalDate date)
    {
        return RedisKey.HOT_KEYWORD_RANKING + ":" + date.format(HOT_KEYWORD_DATE_FORMATTER);
    }

    private Date getHotKeywordExpireAt(LocalDate date)
    {
        return Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
