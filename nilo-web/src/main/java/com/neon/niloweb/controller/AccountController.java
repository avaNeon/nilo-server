package com.neon.niloweb.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.neon.nilocommon.captcha.RedisCaptcha;
import com.neon.nilocommon.entity.constans.Constants;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
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
    private final UserInfoService userInfoService;

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
     * 验证验证码答案<hr/>
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
            userInfoService.register(email, nickName, password);
        }
        finally
        {
            redisCaptcha.deleteCaptcha(captchaKey);
        }
        return ResponseVO.success(null);
    }

    @Operation(summary = "登录接口", description = "检验登录信息和验证码")
    @GetMapping(path = "/login")
    public ResponseVO <Object> login(@Parameter(hidden = true) HttpServletRequest request,
                                     @Parameter(hidden = true) HttpServletResponse response,
                                     @RequestHeader(name = "token", required = false) String token,
                                     @RequestParam(name = "email") @Email(message = "email不符合格式") String email,
                                     @RequestParam(name = "password")
                                     @Pattern(regexp = Constants.PASSWORD_REGEXP, message = "密码格式不合法") String password,
                                     @RequestParam(name = "captchaKey") @NotBlank String captchaKey,
                                     @Parameter(description = "用户填写的验证码结果") @NotBlank @RequestParam(name = "code") String code)
    {
        try
        {
            if (!redisCaptcha.verifyCaptchaCode(captchaKey, code)) throw new BusinessException(ResponseCode.CAPTCHA_FAILED);
            String clientIp = getClientIp(request);
            // TODO 头像、粉丝数、关注数（或许还有硬币数）还没有设置
            TokenUserInfo tokenUserInfo = userInfoService.login(token, email, password, clientIp);
            setCookie(response, Constants.COOKIE_WEB_TOKEN_KEY, tokenUserInfo.getToken(), 7, TimeUnit.DAYS);
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
        return ResponseVO.success(userInfoService.autoLogin(token));
    }

    @Operation(summary = "登出接口", description = "检验token，如果token有效，则从redis中删除token")
    @GetMapping(path = "/logout")
    public ResponseVO <Boolean> logout(@Parameter(hidden = true) HttpServletRequest request,
                                       @Parameter(hidden = true) HttpServletResponse response,
                                       @RequestHeader(name = "token") String token)
    {
        removeCookie(request, response, Constants.COOKIE_WEB_TOKEN_KEY);
        return ResponseVO.success(userInfoService.logout(token));
    }

    /**
     * 获取用户IP
     *
     * @return 用户IP
     */
    private static String getClientIp(HttpServletRequest request)
    {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            // X-Forwarded-For 可能有多个 IP，如： client, proxy1, proxy2 ...
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        ip = request.getHeader("HTTP_CLIENT_IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        return request.getRemoteAddr();
    }

    /**
     * 向Cookie中加入键-值对
     *
     * @param response HttpsServletResponse
     * @param key      键
     * @param value    值
     * @param time     时间长度
     * @param timeUnit 时间长度单位
     */
    private void setCookie(HttpServletResponse response, String key, String value, int time, TimeUnit timeUnit)
    {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(timeUnit.toSeconds(time) > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeUnit.toSeconds(time));
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    /**
     * 删除Cookie中指定键
     *
     * @param request  HttpServletRequest
     * @param response HttpServletResponse
     * @param key      删除的键名
     */
    private void removeCookie(HttpServletRequest request, HttpServletResponse response, String key)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return;
        for (Cookie cookie : cookies)
        {
            if (cookie.getName().equals(key))
            {
                cookie.setMaxAge(0);
                cookie.setPath("/");
                response.addCookie(cookie);
                break;
            }
        }
    }
}
