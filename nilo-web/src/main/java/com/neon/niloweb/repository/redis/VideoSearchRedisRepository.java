package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Repository
public class VideoSearchRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 增加热词搜索次数
     *
     * @param hotKeyword 热词
     */
    public void addHotKeywordCount(String hotKeyword)
    {
        redisTemplate.opsForZSet().incrementScore(RedisKey.HOT_KEYWORD_RANKING, hotKeyword, 1);
    }

    /**
     * 获取热词榜前若干条记录
     *
     * @param range 查询条数
     * @return 前若干条热词，热度从高到低排列
     */
    public List <String> getHotKeywordRanking(Integer range)
    {
        Set <Object> result = redisTemplate.opsForZSet().reverseRange(RedisKey.HOT_KEYWORD_RANKING, 0, range - 1);
        if (result == null || result.isEmpty())
        {
            return List.of();
        }
        else
        {
            return result.stream().map(Object::toString).toList();
        }
    }
}
