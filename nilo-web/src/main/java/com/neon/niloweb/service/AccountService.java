package com.neon.niloweb.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.PageSize;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userInfo.UserGender;
import com.neon.nilocommon.entity.enums.userInfo.UserStatus;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.query.PageCalculator;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


/**
 * 用户信息 业务接口实现
 */
@Service
@RequiredArgsConstructor
public class AccountService
{
    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final RedisTemplate <String, Object> redisTemplate;

    private final Snowflake snowflake;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 注册
     */
    public void register(String email, String nickName, String password)
    {
        if (userInfoMapper.selectByEmail(email) != null || userInfoMapper.selectByNickName(nickName) != null)
            throw new BusinessException(ResponseCode.DATA_EXISTED);
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
        // TODO 设置用户初始硬币数
        userInfoMapper.insert(userInfo);
    }

    /**
     * 登录
     */
    public TokenUserInfo login(String email, String password, String ip)
    {
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        // 校验
        if (userInfo == null || !passwordEncoder.matches(password, userInfo.getPassword()))
            throw new BusinessException(ResponseCode.LOGIN_FAILURE);
        if (userInfo.getStatus() == UserStatus.DISABLE.status) throw new BusinessException(ResponseCode.BANNED_USER);
        // 更新登录信息
        UserInfo updatedUserInfo = new UserInfo();
        updatedUserInfo.setLastLoginIp(ip);
        updatedUserInfo.setLastLoginTime(LocalDateTime.now());
        userInfoMapper.updateByUserId(updatedUserInfo, userInfo.getUserId()); // 就用主键执行UPDATE不用回表，效率更高
        // 设置token
        // 新建一个7天时长的token
        TokenUserInfo tokenUserInfo = BeanUtil.copyProperties(userInfo, TokenUserInfo.class);
        generateAndSaveToken(tokenUserInfo, 7, TimeUnit.DAYS);
        return tokenUserInfo;
    }

    /**
     * 使用Token自动登录
     */
    public TokenUserInfo autoLogin(String token)
    {
        TokenUserInfo tokenUserInfo = (TokenUserInfo) redisTemplate.opsForValue().get(RedisKey.WEB_TOKEN_PREFIX + token);
        if (tokenUserInfo == null) return null;
            // 如果过期时间小于1天，则自动延长至7天
        else if (tokenUserInfo.getExpireTime() - System.currentTimeMillis() < TimeUnit.DAYS.toMillis(1))
        {
            tokenUserInfo.setExpireTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7));
            redisTemplate.opsForValue().set(RedisKey.WEB_TOKEN_PREFIX + token, tokenUserInfo, 7, TimeUnit.DAYS); // 延长时间至7天
        }
        return tokenUserInfo;
    }

    /**
     * 登出
     */
    public Boolean logout(String token)
    {
        return redisTemplate.delete(RedisKey.WEB_TOKEN_PREFIX + token);
    }

    /**
     * 生成Token并保存到Redis中
     */
    private void generateAndSaveToken(TokenUserInfo tokenUserInfo, int time, TimeUnit timeUnit)
    {
        String token = UUID.randomUUID().toString();
        tokenUserInfo.setExpireTime(System.currentTimeMillis() + timeUnit.toMillis(time));
        tokenUserInfo.setToken(token);
        redisTemplate.opsForValue().set(RedisKey.WEB_TOKEN_PREFIX + token, tokenUserInfo, time, timeUnit);
    }

    /**
     * 根据条件分页查询列表
     *
     * @param param 条件参数
     * @return 所有符合条件的结果
     */
    public List <UserInfo> findListByParam(UserInfoQuery param)
    {
        return this.userInfoMapper.selectList(param);
    }

    /**
     * 根据条件分页查询列表
     *
     * @param param 条件参数
     * @return 符合条件的个数
     */
    public Integer findCountByParam(UserInfoQuery param)
    {
        return this.userInfoMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    public PaginationResponseVO <UserInfo> findListByPage(UserInfoQuery param)
    {
        int count = this.findCountByParam(param); // TODO 每次分页查询都要获取表的行数会消耗性能，需要将这个数据保存在redis或者前端中
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();
        PageCalculator page = new PageCalculator(param.getPageNo(), count, pageSize);
        param.setPageCalculator(page);
        List <UserInfo> list = this.findListByParam(param);
        return new PaginationResponseVO <>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
    }

    /**
     * 新增
     */
    public Integer add(UserInfo bean)
    {
        return this.userInfoMapper.insert(bean);
    }

}