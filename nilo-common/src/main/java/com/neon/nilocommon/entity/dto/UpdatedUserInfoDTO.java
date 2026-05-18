package com.neon.nilocommon.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UpdatedUserInfoDTO
{
    @NotBlank
    @Size(min = 1, max = 20)
    private String nickName;

    @NotBlank
    @Size(min = 1, max = 255)
    private String avatar;

    /**
     * 女：0
     * 男：1
     * 未知：2
     */
    @Min(0)
    @Max(2)
    private Integer gender;

    @Size(min = 10, max = 10)
    private String birthday;

    @Size(min = 1, max = 150)
    private String school;

    @Size(min = 1, max = 200)
    private String personalIntroduction;

    @Size(min = 1, max = 300)
    private String noticeInfo;
}
