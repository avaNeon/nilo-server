package com.neon.niloai.entity.vo;

import com.neon.niloai.entity.enums.IntentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 视频问答结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoAskVO
{
    private String answer;

    private List <CitedVideoVO> videos;

    /**
     * 回答里提到的视频片段（第几P、第几秒），问视频内容时才有
     */
    private List <CitedSegmentVO> segments;

    /**
     * 这次请求被判成了哪种行为，便于排查和统计拒绝率
     */
    private IntentType intent;
}
