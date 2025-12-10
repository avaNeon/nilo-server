package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.service.UserInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.neon.nilocommon.entity.vo.ResponseVO.success;


/**
 * 用户信息 Controller
 */
@RequiredArgsConstructor
//@RestController
//@RequestMapping("/userInfo")
public class UserInfoController
{

    private final UserInfoService userInfoService;

    /**
     * 根据UserId查询对象
     */
    @GetMapping("/getUserInfoByUserId/{userId}")
    public ResponseVO <Object> getUserInfoByUserId(@PathVariable("userId") String userId)
    {
        return success(userInfoService.getUserInfoByUserId(userId));
    }

    /**
     * 根据UserId修改对象
     */
    @PutMapping("/updateUserInfoByUserId/{userId}")
    public ResponseVO <Object> updateUserInfoByUserId(UserInfo bean, @PathVariable("userId") String userId)
    {
        userInfoService.updateUserInfoByUserId(bean, userId);
        return success(null);
    }

    /**
     * 根据UserId删除
     */
    @DeleteMapping("/deleteUserInfoByUserId/{userId}")
    public ResponseVO <Object> deleteUserInfoByUserId(@PathVariable("userId") String userId)
    {
        userInfoService.deleteUserInfoByUserId(userId);
        return success(null);
    }

    /**
     * 根据Email查询对象
     */
    @GetMapping("/getUserInfoByEmail/{email}")
    public ResponseVO <Object> getUserInfoByEmail(@PathVariable("email") String email)
    {
        return success(userInfoService.getUserInfoByEmail(email));
    }

    /**
     * 根据Email修改对象
     */
    @PutMapping("/updateUserInfoByEmail/{email}")
    public ResponseVO <Object> updateUserInfoByEmail(UserInfo bean, @PathVariable("email") String email)
    {
        userInfoService.updateUserInfoByEmail(bean, email);
        return success(null);
    }

    /**
     * 根据Email删除
     */
    @DeleteMapping("/deleteUserInfoByEmail/{email}")
    public ResponseVO <Object> deleteUserInfoByEmail(@PathVariable("email") String email)
    {
        userInfoService.deleteUserInfoByEmail(email);
        return success(null);
    }

    /**
     * 根据NickName查询对象
     */
    @GetMapping("/getUserInfoByNickName/{nickname}")
    public ResponseVO <Object> getUserInfoByNickName(@PathVariable("nickname") String nickName)
    {
        return success(userInfoService.getUserInfoByNickName(nickName));
    }

    /**
     * 根据NickName修改对象
     */
    @PutMapping("/updateUserInfoByNickName/{nickname}")
    public ResponseVO <Object> updateUserInfoByNickName(UserInfo bean, @PathVariable("nickname") String nickName)
    {
        userInfoService.updateUserInfoByNickName(bean, nickName);
        return success(null);
    }

    /**
     * 根据NickName删除
     */
    @DeleteMapping("/deleteUserInfoByNickName/{nickname}")
    public ResponseVO <Object> deleteUserInfoByNickName(@PathVariable("nickname") String nickName)
    {
        userInfoService.deleteUserInfoByNickName(nickName);
        return success(null);
    }

    /**
     * 根据条件分页查询<hr/>
     * 不提供不分页查询是防止用非索引字段查询过多条数影响性能
     */
    @GetMapping("/loadDataList")
    public ResponseVO <Object> loadDataList(@Valid UserInfoQuery query)
    {
        return success(userInfoService.findListByPage(query));
    }

    /**
     * 新增
     */
    @PostMapping("/add")
    public ResponseVO <Object> add(@Valid UserInfo bean)
    {
        userInfoService.add(bean);
        return success(null);
    }

    /**
     * 批量新增
     */
    @PostMapping("/addBatch")
    public ResponseVO <Object> addBatch(@RequestBody @Valid List <UserInfo> listBean)
    {
        userInfoService.addBatch(listBean);
        return success(null);
    }

    /**
     * 批量新增/修改 （必须要写全所有的字段，否则无效）
     */
    @PutMapping("/addOrUpdateBatch")
    public ResponseVO <Object> addOrUpdateBatch(@RequestBody @Valid List <UserInfo> listBean)
    {
        userInfoService.addOrUpdateBatch(listBean);
        return success(null);
    }
}