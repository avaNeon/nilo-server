package com.neon.nilocommon.entity.vo.videoInfoDoc;

import com.neon.nilocommon.util.PageCalculator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class VideoSearchResultVO
{
    private PageCalculator pageCalculator;

    private List <VideoInfoDocVO> videoInfoDocList;
}
