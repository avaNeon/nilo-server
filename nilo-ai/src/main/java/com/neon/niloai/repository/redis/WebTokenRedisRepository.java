package com.neon.niloai.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 读取站点登录 token<hr/>
 * token 由 nilo-web 写进 Redis，这里只读。会话不在本服务里签发
 */
@RequiredArgsConstructor
@Repository
public class WebTokenRedisRepository
{
    private final RedisTemplate <String, Object> redisTemplate;

    /**
     * 通过 token 取出登录用户。token 为空或 Redis 里没有时返回 null
     */
    public TokenUserInfo getByToken(String token)
    {
        if (!StringUtils.hasText(token))
        {
            return null;
        }
        Object stored = redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        if (stored instanceof TokenUserInfo tokenUserInfo)
        {
            return tokenUserInfo;
        }
        return null;
    }

    /**
     * @return 登录用户 id
     */
    public long getUserId(String token)
    {
        TokenUserInfo tokenUserInfo = getByToken(token);
        if (tokenUserInfo == null || tokenUserInfo.getUserInfo() == null || tokenUserInfo.getUserInfo().getUserId() == null)
        {
            throw new BusinessException(ResponseCode.NOT_LOGIN);
        }
        return tokenUserInfo.getUserInfo().getUserId();
    }
}
