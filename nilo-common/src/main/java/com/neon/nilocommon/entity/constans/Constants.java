package com.neon.nilocommon.entity.constans;

public class Constants
{
    // 项目名称
    public static final String REDIS_KEY_PREFIX = "nilo:";
    // 验证码相关前缀
    public static final String REDIS_KEY_CAPTCHA = REDIS_KEY_PREFIX + "captcha:";

    // 密码的正则表达式，要求：
    // 8–20 位
    // 必须包含：数字 + 字母
    // 可包含符号：!@#$%^&*()_+-=
    public static final String PASSWORD_REGEXP = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\-=]{8,20}$";

    // user_info表的主键user_id的长度
    public static final Integer USER_INFO_USER_ID_LENGTH = 10;
}
