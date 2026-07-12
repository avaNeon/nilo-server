package com.neon.niloadmin.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class AccountRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 删除用户保存在redis中的统计信息
     *
     * @param userId 用户ID
     */
    public void deleteUserState(Long userId)
    {
        if (userId == null)
        {
            throw new RuntimeException("用户名为空");
        }
        redisTemplate.delete(RedisKey.USER_STATE_PREFIX + userId);
    }

    /**
     * 保存用户账户状态。
     *
     * @param userId 用户ID
     * @param status 用户状态
     */
    public void saveUserStatus(long userId, int status)
    {
        redisTemplate.opsForValue().set(RedisKey.USER_AUTH_PREFIX + userId, status);
    }

}
