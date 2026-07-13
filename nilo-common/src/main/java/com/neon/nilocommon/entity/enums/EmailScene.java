package com.neon.nilocommon.entity.enums;

/**
 * 邮箱验证码场景枚举<hr/>
 * <p>用于区分Redis Key命名空间以及邮件消费者选择对应的邮件模板</p>
 */
public enum EmailScene
{
    /**
     * 注册
     */
    REGISTER,
    /**
     * 找回密码/重置密码
     */
    RESET_PASSWORD
}
