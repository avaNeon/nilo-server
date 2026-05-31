package com.neon.nilocommon.entity.vo.videoInfoDoc;

import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import com.neon.nilocommon.util.PageCalculator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class VideoInfoDocListWithPagination
{
    private PageCalculator pageCalculator;

    private List<VideoInfoDoc> videoInfoDocList;
}
