package com.neon.niloweb.loginState;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userInfo.UserStatus;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.UserInfoMapper;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Web端登录状态工具，校验token存在性和用户账户状态
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LoginState
{
    private final AccountRedisRepository accountRedisRepository;

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final RedissonClient redisson;

    private final WebConfig webConfig;

    /**
     * 检查登录状态。
     */
    public TokenUserInfo getLoginState(String token)
    {
        TokenUserInfo tokenUserInfo = accountRedisRepository.getTokenUserInfoByToken(token);
        if (tokenUserInfo == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }

        Long userId = tokenUserInfo.getUserInfo().getUserId();
        if (userId == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }
        assertUserEnabled(userId);
        return tokenUserInfo;
    }

    /**
     * 检查并返回登录用户ID。
     */
    public long getLoginUserId(String token)
    {
        return getLoginState(token).getUserInfo().getUserId();
    }

    /**
     * 校验用户账户是否可用。
     */
    public void assertUserEnabled(long userId)
    {
        Integer status = loadUserStatus(userId);
        if (Objects.equals(status, UserStatus.DISABLE.status))
        {
            throw new BusinessException(ResponseCode.BANNED_USER);
        }
    }

    /**
     * 获取用户账户状态，Redis缺失时回源MySQL并刷新缓存。
     */
    private Integer loadUserStatus(long userId)
    {
        Integer status = accountRedisRepository.getStatusByUserId(userId);
        if (status != null)
        {
            return status;
        }

        RLock lock = redisson.getLock(RedisKey.USER_AUTH_LOCK_PREFIX + userId);
        boolean locked = false;
        try
        {
            locked = lock.tryLock(5, 20, TimeUnit.SECONDS);
            if (locked)
            {
                status = accountRedisRepository.getStatusByUserId(userId);
                if (status == null)
                {
                    UserInfo userInfo = userInfoMapper.selectByUserId(userId);
                    if (userInfo == null)
                    {
                        throw new BusinessException(ResponseCode.NOT_LOGIN);
                    }
                    status = userInfo.getStatus();
                    accountRedisRepository.saveUserStatus(userId, status, webConfig.getUserInfoExpireDays());
                }
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        finally
        {
            if (locked)
            {
                if (lock.isHeldByCurrentThread())
                {
                    lock.unlock();
                }
                else
                {
                    log.warn("RLock在业务完成之前释放");
                }
            }
        }

        if (status == null)
        {
            throw new BusinessException(ResponseCode.SERVER_ERROR);
        }
        return status;
    }
}
