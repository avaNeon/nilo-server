package com.neon.nilocommon.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户资料快照
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDTO
{
    private Long userId;

    private String nickName;

    private String avatar;

    private Integer status;
}
