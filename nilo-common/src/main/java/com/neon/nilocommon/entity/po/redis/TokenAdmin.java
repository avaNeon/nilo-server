package com.neon.nilocommon.entity.po.redis;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenAdmin
{
    private Integer level;
    private Long expireTime;
    private String token;
}