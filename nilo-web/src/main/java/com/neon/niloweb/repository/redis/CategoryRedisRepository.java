package com.neon.niloweb.repository.redis;

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

    /**
     * 获取分类
     * @return
     * <ul>
     *     <li>
     *         有记录：
     *         <ul>
     *             <li>有数据：返回数据</li>
     *             <li>无数据：返回空列表</li>
     *         </ul>
     *     </li>
     *     <li>
     *         没有记录：返回null
     *     </li>
     * </ul>
     */
    public List <CategoryInfo> getCategoryInfo()
    {
        if (redisTemplate.hasKey(CATEGORIES_INFO)) // 记录存在存在
        {
            Object result = redisTemplate.opsForValue().get(CATEGORIES_INFO);
            if (result instanceof List <?> list) // 有数据，返回数据
            {
                return list.stream().filter(CategoryInfo.class::isInstance).map(CategoryInfo.class::cast).toList();
            }
            else // 无数据，返回空列表
            {
                return new ArrayList <>();
            }
        }
        else // 记录不存在
        {
            return null;
        }
    }

    /**
     * 向Redis中添加分类记录
     * @param list 分类列表
     */
    public void setCategory(List<CategoryInfo> list)
    {
        redisTemplate.opsForValue().set(CATEGORIES_INFO, list);
    }
}
