package com.neon.nilocommon.entity.vo.videoInfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.neon.nilocommon.entity.vo.userInfo.BriefUserInfoVO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BriefVideoInfoVO extends BasicVideoInfo
{
    private BriefUserInfoVO briefUserInfo;

    /**
     * 父级分类ID（内部字段，不对外暴露，用于 Service 层转换）
     */
    @JsonIgnore
    private Integer pCategoryId;

    /**
     * 分类ID（内部字段，不对外暴露，用于 Service 层转换）
     */
    @JsonIgnore
    private Integer categoryId;
}
