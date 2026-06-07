package com.neon.nilocommon.entity.vo.videoInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CollectedVideoInfoVO extends BriefVideoInfoVO
{
    private LocalDateTime collectDate;
}
