package com.neon.nilocommon.entity.enums;


import lombok.Getter;

@Getter
public enum ResponseCodeEnum
{
    SUCCESS(200, "请求成功"),
    WRONG_ARGUMENTS(400, "请求参数错误"),
    INVALID_ARGUMENTS(402, "请求参数不合法"),
    NOT_FOUND(404, "请求地址不存在"),
    SERVER_ERROR(500, "服务器返回错误，请联系管理员"),
    UNKNOWN_ERROR(1000, "未知错误"),
    DATA_EXISTED(1001, "数据已存在");

    private final Integer code;

    private final String msg;

    ResponseCodeEnum(Integer code, String msg)
    {
        this.code = code;
        this.msg = msg;
    }
}
