package com.neon.nilocommon.entity.enums;


import lombok.Getter;

@Getter
public enum ResponseCode
{
    SUCCESS(200, "请求成功"),
    WRONG_ARGUMENTS(400, "请求参数错误"),
    INVALID_ARGUMENTS(402, "请求参数不合法"),
    NOT_FOUND(404, "请求地址不存在"),
    SERVER_ERROR(500, "服务器返回错误，请联系管理员"),
    UNKNOWN_ERROR(1000, "未知错误"),
    DATA_EXISTED(1001, "数据已存在"),
    CAPTCHA_FAILED(1002, "验证码校验失败"),
    LOGIN_FAILURE(1003, "账号或密码错误"),
    BANNED_USER(1004, "账户已禁用"),
    NOT_LOGIN(1005, "未登录"),
    TIME_OUT(1006, "请求超时"),
    NO_PERMISSION(1007, "没有权限"),
    INSUFFICIENT_COIN(1008, "硬币不足"),
    EXPIRE_LOGIN(1009, "登录信息已过期"),
    EMAIL_CODE_SEND_TOO_FREQUENT(1010, "请求过于频繁，请稍后再试"),
    EMAIL_CODE_SEND_LIMIT_EXCEEDED(1011, "验证码请求次数过多，请几小时后再试"),
    EMAIL_CODE_INVALID(1012, "验证码错误或已失效，请重新获取");

    private final Integer code;

    private final String msg;

    ResponseCode(Integer code, String msg)
    {
        this.code = code;
        this.msg = msg;
    }
}
