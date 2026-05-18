package com.neon.nilocommon.entity.vo.userInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BriefUserInfoVO
{
    private Long userId;

    private String nickName;

    private String avatar;

    private String personalIntroduction;
}
