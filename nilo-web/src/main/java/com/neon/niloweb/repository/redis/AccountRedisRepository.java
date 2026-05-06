package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.po.UserState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

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
    public UserState getUserStateUserId(long userId)
    {
        return (UserState) redisTemplate.opsForValue().get(RedisKey.USER_STATE_PREFIX + userId);
    }

    /**
     * 将用户统计信息保存在redis中<hr/>
     * 统计信息包括经常变化的信息，如粉丝数、关注数、硬币数
     *
     * @param tokenUserInfo TokenUserInfo
     * @param expireDays    过期天数
     */
    public void saveUserState(TokenUserInfo tokenUserInfo, int expireDays)
    {
        Long userId = tokenUserInfo.getUserInfo().getUserId();
        if (userId == null)
        {
            throw new RuntimeException("用户名为空");
        }
        UserState userState = new UserState(tokenUserInfo.getFollowerCount(),
                                            tokenUserInfo.getFollowingCount(),
                                            tokenUserInfo.getCurrentCoin());
        redisTemplate.opsForValue().set(RedisKey.USER_STATE_PREFIX + userId, userState, expireDays, TimeUnit.DAYS);
    }

    /**
     * 将用户统计信息保存在redis中<hr/>
     * 统计信息包括经常变化的信息，如粉丝数、关注数、硬币数
     *
     * @param userId     用户ID
     * @param userState  统计信息
     * @param expireDays 过期天数
     */
    public void saveUserState(long userId, UserState userState, int expireDays)
    {
        redisTemplate.opsForValue().set(RedisKey.USER_STATE_PREFIX + userId, userState, expireDays, TimeUnit.DAYS);
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
