package com.neon.niloadmin.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.neon.niloadmin.service.AdminService;
import com.neon.nilocommon.captcha.RedisCaptcha;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.TokenAdmin;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.ServletUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Tag(name = "账户管理")
@RequiredArgsConstructor
@Validated
@RestController
@RequestMapping("/account")
public class AdminController
{

    private final AdminService service;

    private final RedisCaptcha redisCaptcha;


    /**
     * 获取验证码<hr/>
     * 将验证码结果保存在redis中，对应的key和验证码图片封装到Map中，保存在Response的data属性里
     */
    @Operation(summary = "获取验证码接口", description = "访问这个接口获取一个验证码图片")
    @GetMapping(path = "/captcha")
    public ResponseVO <Map <String, String>> captcha()
    {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(200, 70);
        String captchaKey = redisCaptcha.saveCaptchaCode(captcha.getCode());
        Map <String, String> map = new HashMap <>();
        map.put("captchaKey", captchaKey);
        map.put("captchaImg", captcha.getImageBase64());
        return ResponseVO.success(map);
    }

    /**
     * 管理员登录<hr/>
     * 登录后返回一个新token，删除cookie中原始登录token<br/>
     * redis中的token不删除，等它自动过期
     */
    @Operation(summary = "登录接口", description = "检验登录信息和验证码")
    @GetMapping(path = "/login")
    public ResponseVO <TokenAdmin> login(@Parameter(hidden = true) HttpServletRequest request,
                                         @Parameter(hidden = true) HttpServletResponse response,
                                         @RequestParam(name = "account") String account,
                                         @RequestParam(name = "password")
                                         @Pattern(regexp = Constants.PASSWORD_REGEXP, message = "密码格式不合法") String password,
                                         @RequestParam(name = "captchaKey") @NotBlank String captchaKey,
                                         @Parameter(description = "用户填写的验证码结果") @NotBlank @RequestParam(name = "code")
                                         String code)
    {
        try
        {
            if (!redisCaptcha.verifyCaptchaCode(captchaKey, code)) throw new BusinessException(ResponseCode.CAPTCHA_FAILED);
            TokenAdmin tokenAdmin = service.login(account, password);
            ServletUtil.removeCookie(request, response, Constants.COOKIE_TOKEN_ADMIN_KEY);
            ServletUtil.setCookie(response, Constants.COOKIE_TOKEN_ADMIN_KEY, tokenAdmin.getToken(), -1, TimeUnit.DAYS);
            return ResponseVO.success(tokenAdmin);
        }
        finally
        {
            redisCaptcha.deleteCaptcha(captchaKey);
        }
    }

    /**
     * 自动登录
     *
     * @return 是否可以自动登录
     */
    @Operation(summary = "自动登录接口", description = "检验token，如果token有效，则返回是否登录成功")
    @GetMapping(path = "/autoLogin")
    public ResponseVO <TokenAdmin> autoLogin(@RequestHeader(name = "token") String token)
    {
        return ResponseVO.success(service.autoLogin(token));
    }

    /**
     * 登出
     *
     * @return 登出成功/失败，成功则返回true
     */
    @Operation(summary = "登出接口", description = "检验token，如果token有效，则从redis和cookie中删除token")
    @GetMapping(path = "/logout")
    public ResponseVO <Boolean> logout(@Parameter(hidden = true) HttpServletRequest request,
                                       @Parameter(hidden = true) HttpServletResponse response,
                                       @RequestHeader(name = "token") String token)
    {
        ServletUtil.removeCookie(request, response, Constants.COOKIE_TOKEN_ADMIN_KEY);
        return ResponseVO.success(service.logout(token));
    }

}
