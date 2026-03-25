package com.neon.niloadmin.repository.redis;

import com.neon.nilocommon.entity.po.CategoryInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static com.neon.nilocommon.entity.constants.RedisKey.CATEGORIES_INFO;

@RequiredArgsConstructor
@Repository
public class CategoryRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    public boolean hasCategoryInfo()
    {
        return redisTemplate.hasKey(CATEGORIES_INFO);
    }

    public List <CategoryInfo> getCategoryInfo()
    {
        if (!hasCategoryInfo())
        {
            return null;
        }
        Object result = redisTemplate.opsForValue().get(CATEGORIES_INFO);
        if (result instanceof List <?> list)
        {
            return new ArrayList <>(list.stream().filter(CategoryInfo.class::isInstance).map(CategoryInfo.class::cast).toList());
        }
        return new ArrayList <>();
    }

    public void setCategoryInfo(List <CategoryInfo> categoryInfoList)
    {
        redisTemplate.opsForValue().set(CATEGORIES_INFO, categoryInfoList);
    }

    public void deleteCategoryInfo()
    {
        redisTemplate.delete(CATEGORIES_INFO);
    }
}

