package com.neon.niloweb.repository.elasticsearch;

import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoInfoDocListWithPagination;

public interface VideoInfoDocExtensionRepository
{

    /**
     * <b>从ES中搜索记录，并按照指定方式排序</b><hr/>
     * 默认高亮字段名称为"videoName"
     *
     * @param keyword       搜索的关键词
     * @param pageNo        页号
     * @param pageSize      页大小
     * @param useHighlight  是否开始高亮
     * @param sortFieldName 排序的字段名（递减排序）
     * @param useScoreSort  是否追加相关性分数排序
     * @return 搜索结果
     */
    VideoInfoDocListWithPagination searchVideoInfo(String keyword,
                                                   Integer pageNo,
                                                   Integer pageSize,
                                                   Boolean useHighlight,
                                                   String sortFieldName,
                                                   Boolean useScoreSort);
}
