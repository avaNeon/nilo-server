package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.service.UserInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.neon.nilocommon.entity.vo.ResponseVO.success;


/**
 * 用户信息 Controller
 */
@RestController
@RequestMapping("/userInfo")
@RequiredArgsConstructor
public class UserInfoController
{

    private final UserInfoService userInfoService;

    /**
     * 根据条件分页查询
     */
    @GetMapping("/loadDataList")
    public ResponseVO <Object> loadDataList(UserInfoQuery query)
    {
        return success(userInfoService.findListByPage(query));
    }

    /**
     * 新增
     */
    @PostMapping("/add")
    public ResponseVO <Object> add(UserInfo bean)
    {
        userInfoService.add(bean);
        return success(null);
    }

    /**
     * 批量新增
     */
    @PostMapping("/addBatch")
    public ResponseVO <Object> addBatch(@RequestBody List <UserInfo> listBean)
    {
        userInfoService.addBatch(listBean);
        return success(null);
    }

    /**
     * 批量新增/修改
     */
    @PutMapping("/addOrUpdateBatch")
    public ResponseVO <Object> addOrUpdateBatch(@RequestBody List <UserInfo> listBean)
    {
        userInfoService.addBatch(listBean);
        return success(null);
    }

    /**
     * 根据UserId查询对象
     */
    @GetMapping("/getUserInfoByUserId")
    public ResponseVO <Object> getUserInfoByUserId(String userId)
    {
        return success(userInfoService.getUserInfoByUserId(userId));
    }

    /**
     * 根据UserId修改对象
     */
    @PutMapping("/updateUserInfoByUserId")
    public ResponseVO <Object> updateUserInfoByUserId(UserInfo bean, String userId)
    {
        userInfoService.updateUserInfoByUserId(bean, userId);
        return success(null);
    }

    /**
     * 根据UserId删除
     */
    @DeleteMapping("/deleteUserInfoByUserId")
    public ResponseVO <Object> deleteUserInfoByUserId(String userId)
    {
        userInfoService.deleteUserInfoByUserId(userId);
        return success(null);
    }

    /**
     * 根据Email查询对象
     */
    @GetMapping("/getUserInfoByEmail")
    public ResponseVO <Object> getUserInfoByEmail(String email)
    {
        return success(userInfoService.getUserInfoByEmail(email));
    }

    /**
     * 根据Email修改对象
     */
    @PutMapping("/updateUserInfoByEmail")
    public ResponseVO <Object> updateUserInfoByEmail(UserInfo bean, String email)
    {
        userInfoService.updateUserInfoByEmail(bean, email);
        return success(null);
    }

    /**
     * 根据Email删除
     */
    @DeleteMapping("/deleteUserInfoByEmail")
    public ResponseVO <Object> deleteUserInfoByEmail(String email)
    {
        userInfoService.deleteUserInfoByEmail(email);
        return success(null);
    }

    /**
     * 根据NickName查询对象
     */
    @GetMapping("/getUserInfoByNickName")
    public ResponseVO <Object> getUserInfoByNickName(String nickName)
    {
        return success(userInfoService.getUserInfoByNickName(nickName));
    }

    /**
     * 根据NickName修改对象
     */
    @PutMapping("/updateUserInfoByNickName")
    public ResponseVO <Object> updateUserInfoByNickName(UserInfo bean, String nickName)
    {
        userInfoService.updateUserInfoByNickName(bean, nickName);
        return success(null);
    }

    /**
     * 根据NickName删除
     */
    @DeleteMapping("/deleteUserInfoByNickName")
    public ResponseVO <Object> deleteUserInfoByNickName(String nickName)
    {
        userInfoService.deleteUserInfoByNickName(nickName);
        return success(null);
    }
}