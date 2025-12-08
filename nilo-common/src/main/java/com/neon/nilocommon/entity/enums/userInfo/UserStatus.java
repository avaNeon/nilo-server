package com.neon.nilocommon.entity.enums.userInfo;

/**
 * 用户状态枚举类
 */
public enum UserStatus
{
    /**
     * 禁用
     */
    DISABLE(0),
    /**
     * 可用
     */
    ENABLE(1);
    public final int status;

    UserStatus(int status)
    {
        this.status = status;
    }
}
