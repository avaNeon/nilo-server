package com.neon.niloweb.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.email.RedisEmailVerification;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.EmailMessageDTO;
import com.neon.nilocommon.entity.enums.EmailScene;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userInfo.UserGender;
import com.neon.nilocommon.entity.enums.userInfo.UserStatus;
import com.neon.nilocommon.entity.po.FollowInfo;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.po.UserState;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.query.FollowInfoQuery;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.vo.userInfo.BriefUserInfoVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.login.LoginFailureGuard;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.mapper.FollowInfoMapper;
import com.neon.niloweb.mapper.UserInfoMapper;
import com.neon.niloweb.repository.rabbitmq.EmailMqRepository;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService
{
    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final FollowInfoMapper <FollowInfo, FollowInfoQuery> followInfoMapper;

    private final AccountRedisRepository accountRedisRepository;

    private final Snowflake snowflake;

    private final RedissonClient redisson;

    private final WebConfig webConfig;

    private final SystemConfigRedisRepository systemConfigRedisRepository;

    private final LoginState loginState;

    private final RedisEmailVerification redisEmailVerification;

    private final LoginFailureGuard loginFailureGuard;

    private final EmailMqRepository emailMqRepository;

    private final static BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 申请邮箱验证码<hr/>
     * REGISTER场景要求邮箱未被注册；RESET_PASSWORD场景要求邮箱已被注册，
     * 如果邮箱未注册，会自动转为REGISTER场景发送验证码，方便前端无缝引导用户完成注册
     *
     * @param email 邮箱
     * @param scene 前端请求的场景
     * @return 实际生效的场景
     */
    public EmailScene sendEmailCode(String email, EmailScene scene)
    {
        EmailScene resolvedScene = scene;

        // 去邮箱中查一下有没有注册过
        boolean registered = userInfoMapper.selectByEmail(email) != null;

        // 注册场景
        if (scene == EmailScene.REGISTER)
        {
            // 如果注册过，不要让用户继续注册
            if (registered) throw new BusinessException(ResponseCode.DATA_EXISTED);
        }
        // 重置密码场景且没有注册过，转到注册
        else if (!registered)
        {
            resolvedScene = EmailScene.REGISTER;
        }

        // 生成验证码
        String code = redisEmailVerification.generateAndSaveCode(resolvedScene, email);

        // 异步发送验证码
        emailMqRepository.sendEmailCode(new EmailMessageDTO(email, resolvedScene, code));

        // 返回场景
        return resolvedScene;
    }

    /**
     * 注册
     */
    public void register(String email, String nickName, String password, String emailCode)
    {
        // 先验证下验证码是否正确
        redisEmailVerification.verifyCode(EmailScene.REGISTER, email, emailCode);

        if (userInfoMapper.selectByEmail(email) != null || userInfoMapper.selectByNickName(nickName) != null)
        {
            throw new BusinessException(ResponseCode.DATA_EXISTED);
        }
        UserInfo userInfo = new UserInfo();

        // 使用雪花算法生成唯一id
        Long uid = snowflake.nextId();
        userInfo.setUserId(uid);
        userInfo.setEmail(email);
        userInfo.setNickName(nickName);

        // 使用BCrypt加密密码，密文保存密码，密码长度固定60位
        userInfo.setPassword(passwordEncoder.encode(password));

        // 注意时区
        userInfo.setRegisterTime(LocalDateTime.now());
        userInfo.setGender(UserGender.UNKNOWN.gender);

        // 设置用户初始硬币数
        userInfo.setTotalCoin(systemConfigRedisRepository.getSystemConfig().getRegisterCoin());

        userInfoMapper.insert(userInfo);
    }

    /**
     * 登录
     */
    public TokenUserInfo login(String email, String password, String ip)
    {
        loginFailureGuard.checkAllowed(email, ip);

        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo == null || !passwordEncoder.matches(password, userInfo.getPassword()))
        {
            loginFailureGuard.recordFailure(email, ip);
            throw new BusinessException(ResponseCode.LOGIN_FAILURE);
        }
        if (userInfo.getStatus() == UserStatus.DISABLE.status)
        {
            throw new BusinessException(ResponseCode.BANNED_USER);
        }

        loginFailureGuard.clearFailures(email, ip);
        Long userId = userInfo.getUserId();
        // 更新登录信息
        UserInfo updatedUserInfo = new UserInfo();
        updatedUserInfo.setLastLoginIp(ip);
        updatedUserInfo.setLastLoginTime(LocalDateTime.now());
        userInfoMapper.updateByUserId(updatedUserInfo, userId); // 就用主键执行UPDATE不用回表，效率更高

        // 设置token
        // 新建一个7天时长的token
        BriefUserInfoVO briefUserInfoVO = BeanUtil.copyProperties(userInfo, BriefUserInfoVO.class);
        TokenUserInfo tokenUserInfo = new TokenUserInfo();

        // 设置账户状态
        if (accountRedisRepository.getStatusByUserId(userId) == null)
        {
            accountRedisRepository.saveUserStatus(userId, userInfo.getStatus(), webConfig.getUserInfoExpireDays());
        }

        // 先保存brief user info
        tokenUserInfo.setUserInfo(briefUserInfoVO);
        generateAndSaveToken(tokenUserInfo, webConfig.getUserInfoExpireDays());

        return tokenUserInfo;
    }

    /**
     * 通过邮箱验证码重置密码
     */
    public void resetPassword(String email, String emailCode, String newPassword)
    {
        redisEmailVerification.verifyCode(EmailScene.RESET_PASSWORD, email, emailCode);
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo == null) throw new BusinessException(ResponseCode.NOT_FOUND);

        UserInfo updatedUserInfo = new UserInfo();
        updatedUserInfo.setPassword(passwordEncoder.encode(newPassword));
        userInfoMapper.updateByEmail(updatedUserInfo, email);
    }

    /**
     * 使用Token自动登录
     */
    public TokenUserInfo autoLogin(String token)
    {
        TokenUserInfo tokenUserInfo = loginState.getLoginState(token);

        // 如果过期时间小于1天，则自动延长至7天
        if (tokenUserInfo.getExpireTime() - System.currentTimeMillis() < TimeUnit.DAYS.toMillis(1))
        {
            // 先延长token缓存
            accountRedisRepository.extendExpireTime(RedisKey.WEB_TOKEN_PREFIX + token, webConfig.getUserInfoExpireDays());
        }

        return tokenUserInfo;
    }

    /**
     * 登出
     */
    public Boolean logout(String token)
    {
        return accountRedisRepository.deleteTokenUserInfo(token);
    }

    /**
     * 获取统计信息
     *
     * @param userId 用户ID
     * @return 统计信息（有可能为null）
     */
    public UserState getUserStateByUserId(long userId)
    {
        // 查统计信息
        UserState userState = accountRedisRepository.getUserStateByUserId(userId);
        // 如果缓存中没有统计信息
        if (userState == null)
        {
            // 粒度为单个用户
            RLock lock = redisson.getLock(RedisKey.USER_STATE_LOCK_PREFIX + userId);
            boolean locked = false;
            try
            {
                locked = lock.tryLock(5, 20, TimeUnit.SECONDS);
                // 抢到锁了，进行二次检查
                if (locked)
                {
                    userState = accountRedisRepository.getUserStateByUserId(userId);
                    if (userState == null)
                    {
                        // 缓存还没更新，手动更新缓存
                        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
                        Integer followerCount = followInfoMapper.selectFollowerCount(userId);
                        Integer followingCount = followInfoMapper.selectFollowingCount(userId);
                        userState = new UserState(followerCount, followingCount, userInfo.getCurrentCoin());
                        accountRedisRepository.saveUserState(userId, userState, webConfig.getUserInfoExpireDays());
                    }
                }
            }
            catch (InterruptedException e)
            {
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
        }
        return userState;
    }

    /**
     * 生成Token并保存到Redis中
     */
    public void generateAndSaveToken(TokenUserInfo tokenUserInfo, int expireDays)
    {
        String token = UUID.randomUUID().toString();
        tokenUserInfo.setToken(token);
        accountRedisRepository.setUserInfoByToken(token, tokenUserInfo, expireDays);
    }

    /**
     * 新增
     */
    public Integer add(UserInfo bean)
    {
        return this.userInfoMapper.insert(bean);
    }

}
