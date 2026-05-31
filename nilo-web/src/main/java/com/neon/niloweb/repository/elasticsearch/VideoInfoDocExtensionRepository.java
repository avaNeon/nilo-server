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
     * @return 搜索结果
     */
    VideoInfoDocListWithPagination searchVideoInfo(String keyword,
                                                   Integer pageNo,
                                                   Integer pageSize,
                                                   Boolean useHighlight,
                                                   String sortFieldName);

    /**
     * 根据 videoId 增加一条 document 的 playCount 值
     *
     * @param videoId   视频ID
     * @param increment 播放数增量
     */
    void increasePlayCountByVideoId(long videoId, int increment);

    /**
     * 根据 videoId 增加一条 document 的 danmakuCount 值
     *
     * @param videoId   视频ID
     * @param increment 弹幕数增量
     */
    void increaseDanmakuCountByVideoId(long videoId, int increment);

    /**
     * 根据 videoId 减少一条 document 的 danmakuCount 值
     *
     * @param videoId   视频ID
     * @param decrement 弹幕数减少量
     */
    void decreaseDanmakuCountByVideoId(long videoId, int decrement);

    /**
     * 根据 videoId 增加一条 document 的 collectCount 值
     *
     * @param videoId   视频ID
     * @param increment 收藏数增量
     */
    void increaseCollectCountByVideoId(long videoId, int increment);

    /**
     * 根据 videoId 减少一条 document 的 collectCount 值
     *
     * @param videoId   视频ID
     * @param decrement 收藏数减少量
     */
    void decreaseCollectCountByVideoId(long videoId, int decrement);
}
