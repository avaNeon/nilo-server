package com.neon.niloadmin.controller;

import com.neon.niloadmin.service.AccountService;
import com.neon.nilocommon.entity.po.UserInfo;
import com.neon.nilocommon.entity.query.UserInfoQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户账户管理")
@RequiredArgsConstructor
@Validated
@RequestMapping(path = "/account")
@RestController
public class AccountController
{
    private final AccountService accountService;

    @Operation(summary = "获取用户账户列表")
    @PostMapping(path = "/list")
    public ResponseVO <List <UserInfo>> loadUserInfoList(@RequestBody UserInfoQuery query)
    {
        return ResponseVO.success(accountService.loadUserInfoList(query));
    }

    @Operation(summary = "查询用户账户总数")
    @PostMapping(path = "/count")
    public ResponseVO <Integer> countUserInfo(@RequestBody UserInfoQuery query)
    {
        return ResponseVO.success(accountService.countUserInfo(query));
    }

    @Operation(summary = "修改用户账户状态")
    @PutMapping(path = "/status")
    public ResponseVO <Object> changeUserStatus(@RequestParam(name = "userId") @NotNull Long userId,
                                                @RequestParam(name = "status") @NotNull @Min(0) @Max(1) Integer status)
    {
        accountService.changeUserStatus(userId, status);
        return ResponseVO.success(null);
    }
}
