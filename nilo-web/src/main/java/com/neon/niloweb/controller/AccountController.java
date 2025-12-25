package com.neon.niloweb.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.neon.nilocommon.captcha.RedisCaptcha;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.ServletUtil;
import com.neon.niloweb.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
     * @param code 用户的填入的验证码
     */
    @Operation(summary = "注册接口", description = "检验用户信息和验证码")
    @GetMapping(path = "/register")
    public ResponseVO <Object> register(
            @RequestParam(name = "email") @NotBlank(message = "email不能为空") @Email(message = "email不符合格式")
            @Size(max = 150, message = "email长度过大") String email,
            @RequestParam(name = "nickName") @NotBlank(message = "nickName不能为空") @Size(max = 20, message = "nickName长度过大")
            String nickName,
            @RequestParam(name = "password") @Pattern(regexp = Constants.PASSWORD_REGEXP, message = "密码格式不合法") String password,
            @RequestParam(name = "captchaKey") @NotBlank String captchaKey,
            @Parameter(description = "用户填写的验证码结果") @NotBlank @RequestParam(name = "code") String code)
    {
        try
        {
            if (!redisCaptcha.verifyCaptchaCode(captchaKey, code)) throw new BusinessException(ResponseCode.CAPTCHA_FAILED);
            accountService.register(email, nickName, password);
        }
        finally
        {
            redisCaptcha.deleteCaptcha(captchaKey);
        }
        return ResponseVO.success(null);
    }

    /**
     * 登录<hr/>
     * 登录成功后会返回新生成的一个token，并且删除cookie原来的登录token<br/>
     * 不过redis中这个token没有删除，因为我们也不知道token具体值是多少，不过它会自动过期
     * @return 将查询到的用户数据返回给前端
     */
    @Operation(summary = "登录接口", description = "检验登录信息和验证码")
    @GetMapping(path = "/login")
    public ResponseVO <TokenUserInfo> login(@Parameter(hidden = true) HttpServletRequest request,
                                            @Parameter(hidden = true) HttpServletResponse response,
                                            @RequestParam(name = "email") @Email(message = "email不符合格式") String email,
                                            @RequestParam(name = "password")
                                            @Pattern(regexp = Constants.PASSWORD_REGEXP, message = "密码格式不合法") String password,
                                            @RequestParam(name = "captchaKey") @NotBlank String captchaKey,
                                            @Parameter(description = "用户填写的验证码结果") @NotBlank @RequestParam(name = "code")
                                            String code)
    {
        try
        {
            if (!redisCaptcha.verifyCaptchaCode(captchaKey, code)) throw new BusinessException(ResponseCode.CAPTCHA_FAILED);
            String clientIp = ServletUtil.getClientIp(request);
            // TODO 头像、粉丝数、关注数（或许还有硬币数）还没有设置
            TokenUserInfo tokenUserInfo = accountService.login(email, password, clientIp);
            ServletUtil.removeCookie(request, response, Constants.ADMIN_COOKIE_TOKEN_KEY);
            ServletUtil.setCookie(response, Constants.WEB_COOKIE_TOKEN_KEY, tokenUserInfo.getToken(), 7, TimeUnit.DAYS);
            return ResponseVO.success(tokenUserInfo);
        }
        finally
        {
            redisCaptcha.deleteCaptcha(captchaKey);
        }
    }

    @Operation(summary = "自动登录接口", description = "检验token，如果token有效，则返回用户信息")
    @GetMapping(path = "/autoLogin")
    public ResponseVO <Object> autoLogin(@RequestHeader(name = "token") String token)
    {
        return ResponseVO.success(accountService.autoLogin(token));
    }

    @Operation(summary = "登出接口", description = "检验token，如果token有效，则从redis和cookie中删除token")
    @GetMapping(path = "/logout")
    public ResponseVO <Boolean> logout(@Parameter(hidden = true) HttpServletRequest request,
                                       @Parameter(hidden = true) HttpServletResponse response,
                                       @RequestHeader(name = "token") String token)
    {
        ServletUtil.removeCookie(request, response, Constants.WEB_COOKIE_TOKEN_KEY);
        return ResponseVO.success(accountService.logout(token));
    }

}
