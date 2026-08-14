package com.neon.niloweb.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.neon.nilocommon.annotation.RateLimit;
import com.neon.nilocommon.entity.enums.RateLimitType;
import com.neon.nilocommon.captcha.RedisCaptcha;
import com.neon.niloweb.annotation.Authorized;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.EmailCodeRequestDTO;
import com.neon.nilocommon.entity.dto.LoginUserInfoDTO;
import com.neon.nilocommon.entity.dto.RegisterUserInfoDTO;
import com.neon.nilocommon.entity.dto.ResetPasswordDTO;
import com.neon.nilocommon.entity.enums.EmailScene;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.UserState;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.loginState.LoginState;
import com.neon.nilocommon.util.ServletUtil;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class AccountController
{
    private final AccountService accountService;

    private final RedisCaptcha redisCaptcha;

    private final LoginState loginState;

    private final WebConfig webConfig;

    /**
     * 获取验证码<hr/>
     * 将验证码结果保存在redis中，对应的key和验证码图片封装到Map中，保存在Response的data属性里
     */
    @RateLimit
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
     * 申请邮箱验证码<hr/>
     * REGISTER场景要求邮箱未注册；RESET_PASSWORD场景如果邮箱未注册，会自动转为REGISTER场景发送验证码<br/>
     * 前端应始终以响应中的data（实际生效场景）而不是自己传入的scene来决定下一步展示的表单
     *
     * @param emailCodeRequestDTO 邮箱和场景
     */
    @RateLimit
    @Operation(summary = "申请邮箱验证码接口", description = "用于注册或找回密码，返回实际生效的场景")
    @PostMapping(path = "/email")
    public ResponseVO <EmailScene> sendEmailCode(@RequestBody @Valid EmailCodeRequestDTO emailCodeRequestDTO)
    {
        return ResponseVO.success(accountService.sendEmailCode(emailCodeRequestDTO.getEmail(), emailCodeRequestDTO.getScene()));
    }

    /**
     * 注册<hr/>
     * 验证邮箱验证码是否正确
     *
     * @param registerUserInfoDTO 前端传入的注册信息，包括邮箱、昵称、密码和邮箱验证码
     */
    @RateLimit
    @Operation(summary = "注册接口", description = "检验用户信息和邮箱验证码")
    @PostMapping(path = "/register")
    public ResponseVO <Void> register(@RequestBody @Valid RegisterUserInfoDTO registerUserInfoDTO)
    {
        accountService.register(registerUserInfoDTO.getEmail(),
                                registerUserInfoDTO.getNickName(),
                                registerUserInfoDTO.getPassword(),
                                registerUserInfoDTO.getEmailCode());
        return ResponseVO.success();
    }

    /**
     * 重置密码<hr/>
     * 验证邮箱验证码是否正确，通过后更新密码
     *
     * @param resetPasswordDTO 邮箱、邮箱验证码和新密码
     */
    @RateLimit
    @Operation(summary = "重置密码接口", description = "通过邮箱验证码重置密码，需要先调用申请邮箱验证码接口（scene传RESET_PASSWORD）")
    @PostMapping(path = "/reset")
    public ResponseVO <Object> resetPassword(@RequestBody @Valid ResetPasswordDTO resetPasswordDTO)
    {
        accountService.resetPassword(resetPasswordDTO.getEmail(),
                                     resetPasswordDTO.getEmailCode(),
                                     resetPasswordDTO.getNewPassword());
        return ResponseVO.success(null);
    }

    /**
     * 登录<hr/>
     * 登录成功后会返回新生成的一个token，并且删除cookie原来的登录token<br/>
     * 不过redis中这个token没有删除，因为我们也不知道token具体值是多少，不过它会自动过期
     *
     * @return 将查询到的用户数据返回给前端
     */
    @RateLimit
    @Operation(summary = "登录接口", description = "检验登录信息和验证码")
    @PostMapping(path = "/login")
    public ResponseVO <TokenUserInfo> login(@Parameter(hidden = true) HttpServletRequest request,
                                            @Parameter(hidden = true) HttpServletResponse response,
                                            @RequestBody @Valid LoginUserInfoDTO loginUserInfoDTO)
    {
        try
        {
            if (!redisCaptcha.verifyCaptchaCode(loginUserInfoDTO.getCaptchaKey(), loginUserInfoDTO.getCode()))
            {
                throw new BusinessException(ResponseCode.CAPTCHA_FAILED);
            }
            String clientIp = ServletUtil.getClientIp(request);
            TokenUserInfo tokenUserInfo = accountService.login(loginUserInfoDTO.getEmail(),
                                                               loginUserInfoDTO.getPassword(),
                                                               clientIp);
            ServletUtil.removeCookie(request, response, Constants.WEB_COOKIE_TOKEN_KEY);
            ServletUtil.setCookie(response,
                                  Constants.WEB_COOKIE_TOKEN_KEY,
                                  tokenUserInfo.getToken(),
                                  webConfig.getUserInfoExpireDays(),
                                  TimeUnit.DAYS);
            return ResponseVO.success(tokenUserInfo);
        }
        finally
        {
            redisCaptcha.deleteCaptcha(loginUserInfoDTO.getCaptchaKey());
        }
    }

    @RateLimit(by = RateLimitType.USER)
    @Authorized
    @Operation(summary = "自动登录接口", description = "检验token，如果token有效，则返回用户信息")
    @GetMapping(path = "/autoLogin")
    public ResponseVO <TokenUserInfo> autoLogin(@RequestHeader(name = "token") @NotEmpty String token)
    {
        return ResponseVO.success(accountService.autoLogin(token));
    }

    @RateLimit(by = RateLimitType.USER)
    @Operation(summary = "登出接口", description = "检验token，如果token有效，则从redis和cookie中删除token")
    @GetMapping(path = "/logout")
    public ResponseVO <Boolean> logout(@Parameter(hidden = true) HttpServletRequest request,
                                       @Parameter(hidden = true) HttpServletResponse response,
                                       @RequestHeader(name = "token") @NotEmpty String token)
    {
        ServletUtil.removeCookie(request, response, Constants.WEB_COOKIE_TOKEN_KEY);
        return ResponseVO.success(accountService.logout(token));
    }

    /**
     * 获取统计信息<hr/>
     * 从登录/自动登录中拆解出来，防止双检加锁的开销让前端的登录过程有较大延迟
     *
     * @param token token
     * @return 用户统计信息
     */
    @RateLimit(by = RateLimitType.USER)
    @Authorized
    @Operation(summary = "获取统计信息", description = "获取统计信息，返回值可能为null，代表失败的情况")
    @GetMapping(path = "/state")
    public ResponseVO <UserState> userState(@RequestHeader(name = "token") @NotEmpty String token)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(accountService.getUserStateByUserId(userId));
    }
}
