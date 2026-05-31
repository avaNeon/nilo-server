package com.neon.nilocommon.entity.vo.userInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * <b>简略用户信息</b><hr/>
 * 在视频列表页面中展示的用户信息
 */
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
