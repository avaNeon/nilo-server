package com.neon.niloweb.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.neon.nilocommon.captcha.RedisCaptcha;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.LoginUserInfoDTO;
import com.neon.nilocommon.entity.dto.RegisterUserInfoDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.UserState;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.loginState.LoginState;
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
     * 注册<hr/>
     * 验证验证码答案<br/>
     * 根据传入的captchaKey找到对应的redis中的captchaKey，验证用户验证码是否正确
     *
     * @param registerUserInfoDTO 前端传入的注册信息，包括邮箱、昵称、密码、验证码答案和验证码key
     */
    @Operation(summary = "注册接口", description = "检验用户信息和验证码")
    @PostMapping(path = "/register")
    public ResponseVO <Object> register(@RequestBody @Valid RegisterUserInfoDTO registerUserInfoDTO)
    {
        try
        {
            if (!redisCaptcha.verifyCaptchaCode(registerUserInfoDTO.getCaptchaKey(), registerUserInfoDTO.getCode()))
                throw new BusinessException(ResponseCode.CAPTCHA_FAILED);
            accountService.register(registerUserInfoDTO.getEmail(),
                                    registerUserInfoDTO.getNickName(),
                                    registerUserInfoDTO.getPassword());
        }
        finally
        {
            redisCaptcha.deleteCaptcha(registerUserInfoDTO.getCaptchaKey());
        }
        return ResponseVO.success(null);
    }

    /**
     * 登录<hr/>
     * 登录成功后会返回新生成的一个token，并且删除cookie原来的登录token<br/>
     * 不过redis中这个token没有删除，因为我们也不知道token具体值是多少，不过它会自动过期
     *
     * @return 将查询到的用户数据返回给前端
     */
    @Operation(summary = "登录接口", description = "检验登录信息和验证码")
    @PostMapping(path = "/login")
    public ResponseVO <TokenUserInfo> login(@Parameter(hidden = true) HttpServletRequest request,
                                            @Parameter(hidden = true) HttpServletResponse response,
                                            @RequestBody @Valid LoginUserInfoDTO loginUserInfoDTO)
    {
        try
        {
            if (!redisCaptcha.verifyCaptchaCode(loginUserInfoDTO.getCaptchaKey(), loginUserInfoDTO.getCode()))
                throw new BusinessException(ResponseCode.CAPTCHA_FAILED);
            String clientIp = ServletUtil.getClientIp(request);
            // TODO 头像、粉丝数、关注数（或许还有硬币数）还没有设置
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

    @Operation(summary = "自动登录接口", description = "检验token，如果token有效，则返回用户信息")
    @GetMapping(path = "/autoLogin")
    public ResponseVO <TokenUserInfo> autoLogin(@RequestHeader(name = "token") @NotEmpty String token)
    {
        return ResponseVO.success(accountService.autoLogin(token));
    }

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
    @Operation(summary = "获取统计信息", description = "获取统计信息，返回值可能为null，代表失败的情况")
    @GetMapping(path = "/state")
    public ResponseVO <UserState> userState(@RequestHeader(name = "token") @NotEmpty String token)
    {
        long userId = loginState.getLoginUserId(token);
        return ResponseVO.success(accountService.getUserStateByUserId(userId));
    }
}
