package com.neon.niloweb.service;

import com.neon.nilocommon.entity.constans.Constants;
import com.neon.nilocommon.entity.enums.PageSize;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.userInfo.UserGender;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.query.PageCalculator;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.mapper.UserInfoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 用户信息 业务接口实现
 */
@Service
@RequiredArgsConstructor
public class UserInfoService
{

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 注册
     */
    public void register(String email, String nickName, String password)
    {
        if (userInfoMapper.selectByEmail(email) != null || userInfoMapper.selectByNickName(nickName) != null)
            throw new BusinessException(ResponseCode.DATA_EXISTED);
        UserInfo userInfo = new UserInfo();
        String uid = RandomStringUtils.random(Constants.USER_INFO_USER_ID_LENGTH, true, true);
        // 只要生成的uid重复，那么就重新生成
        while (userInfoMapper.selectByUserId(uid) != null) uid = RandomStringUtils.random(Constants.USER_INFO_USER_ID_LENGTH, true, true);
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
     * 根据UserId获取对象
     */
    public UserInfo getUserInfoByUserId(String userId)
    {
        return this.userInfoMapper.selectByUserId(userId);
    }

    /**
     * 根据UserId修改
     */
    public Integer updateUserInfoByUserId(UserInfo bean, String userId)
    {
        return this.userInfoMapper.updateByUserId(bean, userId);
    }

    /**
     * 根据UserId删除
     */
    public Integer deleteUserInfoByUserId(String userId)
    {
        return this.userInfoMapper.deleteByUserId(userId);
    }

    /**
     * 根据Email获取对象
     */
    public UserInfo getUserInfoByEmail(String email)
    {
        return this.userInfoMapper.selectByEmail(email);
    }

    /**
     * 根据Email修改
     */
    public Integer updateUserInfoByEmail(UserInfo bean, String email)
    {
        return this.userInfoMapper.updateByEmail(bean, email);
    }

    /**
     * 根据Email删除
     */
    public Integer deleteUserInfoByEmail(String email)
    {
        return this.userInfoMapper.deleteByEmail(email);
    }

    /**
     * 根据NickName获取对象
     */
    public UserInfo getUserInfoByNickName(String nickName)
    {
        return this.userInfoMapper.selectByNickName(nickName);
    }

    /**
     * 根据NickName修改
     */
    public Integer updateUserInfoByNickName(UserInfo bean, String nickName)
    {
        return this.userInfoMapper.updateByNickName(bean, nickName);
    }

    /**
     * 根据NickName删除
     */
    public Integer deleteUserInfoByNickName(String nickName)
    {
        return this.userInfoMapper.deleteByNickName(nickName);
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

    /**
     * 批量新增
     */
    public Integer addBatch(List <UserInfo> listBean)
    {
        if (listBean == null || listBean.isEmpty())
        {
            return 0;
        }
        return this.userInfoMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    public Integer addOrUpdateBatch(List <UserInfo> listBean)
    {
        if (listBean == null || listBean.isEmpty())
        {
            return 0;
        }
        return this.userInfoMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    public Integer updateByParam(UserInfo bean, @Valid UserInfoQuery param)
    {
        return this.userInfoMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    public Integer deleteByParam(@Valid UserInfoQuery param)
    {
        return this.userInfoMapper.deleteByParam(param);
    }
}