package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Admin
{
    private String account;
    private String password;
    private Integer level;
}
