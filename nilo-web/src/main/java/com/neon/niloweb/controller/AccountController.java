package com.neon.niloweb.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.captcha.RedisCaptcha;
import com.neon.niloweb.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "账户管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/account")
public class AccountController
{
    private final UserInfoService userInfoService;
    private final RedisTemplate <String, String> redisTemplate;
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
     * 根据之前发的session-id和内存中的session-id做对比，找出对应的验证码答案
     *
     * @param code 用户的验证码答案
     */
    @Operation(summary = "注册接口", description = "检验验证码，在data字段中保存验证码通过/失败")
    @GetMapping(path = "/register")
    public ResponseVO <Boolean> register(@Parameter(hidden = true) HttpSession session,
                                         @Parameter(description = "用户填写的验证码结果") @RequestParam(name = "code") String code)
    {
        // TODO 在分布式环境下将验证码结果放在redis中保存
        return ResponseVO.success(session.getAttribute("verification-code").toString().equalsIgnoreCase(code));
    }
}
