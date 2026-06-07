package com.neon.nilocommon.entity.vo.userInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FollowUserInfo extends BriefUserInfoVO
{
    private Boolean followed;

    private Boolean following;
}
