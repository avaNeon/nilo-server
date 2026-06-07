package com.neon.nilocommon.entity.vo.videoInfoDoc;

import com.neon.nilocommon.entity.vo.userInfo.BriefUserInfoVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ES视频信息VO
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class VideoInfoDocVO
{
    private BriefUserInfoVO briefUserInfo;

    private Long videoId;

    private String videoCover;

    private String videoName;

    private Integer duration;

    private LocalDateTime lastUpdateTime;

    private String categoryNumber;

    private List <String> tags;

    private Integer playCount;

    private Integer danmakuCount;

    private Integer collectCount;
}
