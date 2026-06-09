package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.po.UserState;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Repository
public class AccountRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 通过token从Redis获得除统计信息外的用户记录
     *
     * @param token token
     * @return 用户记录
     */
    public TokenUserInfo getTokenUserInfoByToken(String token)
    {
        Object result = redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        if (result instanceof TokenUserInfo tokenUserInfo)
        {
            return tokenUserInfo;
        }
        else
        {
            return null;
        }
    }

    /**
     * 根据用户ID获取统计信息
     *
     * @param userId 用户ID
     * @return 统计信息
     */
    public UserState getUserStateByUserId(long userId)
    {
        return (UserState) redisTemplate.opsForValue().get(RedisKey.USER_STATE_PREFIX + userId);
    }

    /**
     * 根据用户ID获取账户状态。
     */
    public Integer getStatusByUserId(long userId)
    {
        Object result = redisTemplate.opsForValue().get(RedisKey.USER_AUTH_PREFIX + userId);
        if (result instanceof Integer status)
        {
            return status;
        }
        return null;
    }

    /**
     * 保存账户状态。
     */
    public void saveUserStatus(Long userId, Integer status, int expireDays)
    {
        if (userId == null)
        {
            throw new RuntimeException("用户ID为空");
        }
        redisTemplate.opsForValue().set(RedisKey.USER_AUTH_PREFIX + userId, status, expireDays, TimeUnit.DAYS);
    }

    /**
     * 将用户统计信息保存在redis中<hr/>
     * 统计信息包括经常变化的信息，如粉丝数、关注数、硬币数
     *
     * @param userId     用户ID
     * @param userState  统计信息
     * @param expireDays 过期天数
     */
    public void saveUserState(Long userId, UserState userState, int expireDays)
    {
        if (userId == null)
        {
            throw new RuntimeException("用户名为空");
        }
        redisTemplate.opsForValue().set(RedisKey.USER_STATE_PREFIX + userId, userState, expireDays, TimeUnit.DAYS);
    }

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
     * 批量删除用户保存在redis中的统计信息<hr/>
     * 自动排除为null的元素
     *
     * @param userIdList 用户ID列表
     */
    public void deleteUserStateBatch(List <Long> userIdList)
    {
        if (userIdList == null)
        {
            throw new RuntimeException("列表为空");
        }
        userIdList.stream().filter(Objects::nonNull).forEach(userId -> redisTemplate.delete(RedisKey.USER_STATE_PREFIX + userId));
    }

    /**
     * 设置用户信息
     *
     * @param token         token
     * @param tokenUserInfo 用户信息
     * @param expireDays    有效时间（单位：天）
     */
    public void setUserInfoByToken(String token, TokenUserInfo tokenUserInfo, int expireDays)
    {
        tokenUserInfo.setExpireTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(expireDays));
        redisTemplate.opsForValue().set(RedisKey.WEB_TOKEN_PREFIX + token, tokenUserInfo, expireDays, TimeUnit.DAYS);
    }

    /**
     * 将指定key延长指定时间
     *
     * @param redisKey   redis key
     * @param expireDays 延长时间（单位：天）
     */
    public void extendExpireTime(String redisKey, int expireDays)
    {
        redisTemplate.expire(redisKey, expireDays, TimeUnit.DAYS);
    }

    /**
     * 通过token删除用户在Redis保存的信息
     *
     * @param token token
     * @return 是否删除
     */
    public Boolean deleteTokenUserInfo(String token)
    {
        return redisTemplate.delete(RedisKey.WEB_TOKEN_PREFIX + token);
    }
}
