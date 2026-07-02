package com.neon.nilocommon.entity.dto;

import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class VideoInfoArchiveAdminJoinDTO
{
    private Long videoId;

    private String videoCover;

    private String videoName;

    private Long userId;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdateTime;

    private LocalDateTime deleteTime;

    private Integer deleterType;

    private String deleteDetail;

    private Integer pCategoryId;

    private Integer categoryId;

    private Short postType;

    private String originInfo;

    private String tags;

    private String introduction;

    private String interaction;

    private Integer duration;

    private Integer playCount;

    private Integer likeCount;

    private Integer danmakuCount;

    private Integer commentCount;

    private Integer coinCount;

    private Integer collectCount;

    private String nickName;

    private String avatar;
}
