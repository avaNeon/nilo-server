package com.neon.nilocommon.entity.dto;

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