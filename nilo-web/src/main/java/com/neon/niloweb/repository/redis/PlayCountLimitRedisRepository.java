package com.neon.niloweb.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@RequiredArgsConstructor
@Repository
public class PlayCountLimitRedisRepository
{
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 播放统计限流的 Lua 脚本（固定窗口计数）<hr/>
     * 先检查会话和IP两个维度，都未超限才同时自增；一旦任意一个维度超限就直接拒绝，不产生任何副作用<br/>
     * 这样被会话维度拦下的请求不会消耗IP维度的额度，避免同一IP下的其他用户被连带限流<br/>
     * 如果放行，返回0<br/>
     * 如果会话维度超限，返回1<br/>
     * 如果IP维度超限，返回2<br/>
     */
    private static final DefaultRedisScript <Long> PLAY_COUNT_LIMIT_SCRIPT = new DefaultRedisScript <>("""
                                                                                                               local sessionKey = KEYS[1]
                                                                                                               local ipKey = KEYS[2]
                                                                                                               local sessionLimit = tonumber(ARGV[1])
                                                                                                               local sessionTtl = tonumber(ARGV[2])
                                                                                                               local ipLimit = tonumber(ARGV[3])
                                                                                                               local ipTtl = tonumber(ARGV[4])
                                                                                                               
                                                                                                               if tonumber(redis.call('GET', sessionKey) or '0') >= sessionLimit then
                                                                                                                   return 1
                                                                                                               end
                                                                                                               if tonumber(redis.call('GET', ipKey) or '0') >= ipLimit then
                                                                                                                   return 2
                                                                                                               end
                                                                                                               
                                                                                                               -- 第一次创建key时设置过期时间，窗口从第一次放行开始计时
                                                                                                               if redis.call('INCR', sessionKey) == 1 then
                                                                                                                   redis.call('EXPIRE', sessionKey, sessionTtl)
                                                                                                               end
                                                                                                               if redis.call('INCR', ipKey) == 1 then
                                                                                                                   redis.call('EXPIRE', ipKey, ipTtl)
                                                                                                               end
                                                                                                               
                                                                                                               return 0
                                                                                                               """, Long.class);

    /**
     * 尝试获取一次播放统计的记录资格<hr/>
     * 同一 sessionId-videoId 和同一 IP-videoId 分别按各自的窗口和上限限流
     *
     * @param videoId             视频ID
     * @param sessionId           会话ID
     * @param ip                  客户端IP
     * @param sessionLimit        会话维度窗口内最多放行次数
     * @param sessionWindowSecond 会话维度窗口，单位：s
     * @param ipLimit             IP维度窗口内最多放行次数
     * @param ipWindowSecond      IP维度窗口，单位：s
     * @return true: 放行；false: 会话维度或IP维度超限
     */
    public boolean tryAcquire(long videoId,
                              String sessionId,
                              String ip,
                              int sessionLimit,
                              int sessionWindowSecond,
                              int ipLimit,
                              int ipWindowSecond)
    {
        String sessionKey = RedisKey.PLAY_COUNT_LIMIT_PREFIX + "{" + videoId + "}:session:" + sessionId;
        String ipKey = RedisKey.PLAY_COUNT_LIMIT_PREFIX + "{" + videoId + "}:ip:" + ip;

        Long result = stringRedisTemplate.execute(PLAY_COUNT_LIMIT_SCRIPT,
                                                  List.of(sessionKey, ipKey),
                                                  String.valueOf(sessionLimit),
                                                  String.valueOf(sessionWindowSecond),
                                                  String.valueOf(ipLimit),
                                                  String.valueOf(ipWindowSecond));

        return result == 0L;
    }
}
