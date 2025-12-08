package com.neon.nilocommon.entity.enums.userInfo;

public enum UserGender
{
    MALE(0), FEMALE(1), UNKNOWN(2);

    public final int gender;

    UserGender(int gender)
    {
        this.gender = gender;
    }
}
