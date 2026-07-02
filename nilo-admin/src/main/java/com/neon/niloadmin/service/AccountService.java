package com.neon.niloadmin.service;

import com.neon.niloadmin.mapper.UserInfoMapper;
import com.neon.niloadmin.repository.redis.AccountRedisRepository;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.PageCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class AccountService
{
    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final AccountRedisRepository accountRedisRepository;

    /**
     * 分页查询用户账户信息
     */
    public List <UserInfo> loadUserInfoList(UserInfoQuery query)
    {
        if (query == null || query.getPageNo() == null || query.getPageSize() == null)
        {
            throw new BusinessException("pageNo和pageSize不能为空");
        }
        if (query.getPageSize() > 100)
        {
            throw new BusinessException("pageSize不能超过100");
        }

        // 按照注册时间倒序排列
        query.setOrderBy("u.register_time DESC");

        query.setPageCalculator(new PageCalculator(query.getPageNo(), query.getPageSize()));

        return userInfoMapper.selectList(query);
    }

    /**
     * 根据查询条件统计用户总数
     */
    public Integer countUserInfo(UserInfoQuery query)
    {
        return userInfoMapper.selectCount(query);
    }

    /**
     * 修改用户账户状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeUserStatus(long userId, int status)
    {
        UserInfo userInfo = new UserInfo();
        userInfo.setStatus(status);

        // MySQL更新
        Integer count = userInfoMapper.updateByUserId(userInfo, userId);
        if (count == null || count == 0)
        {
            throw new BusinessException("用户不存在");
        }

        accountRedisRepository.saveUserStatus(userId, status);
    }
}
