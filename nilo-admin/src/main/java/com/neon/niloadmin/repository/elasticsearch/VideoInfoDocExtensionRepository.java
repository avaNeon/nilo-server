package com.neon.niloadmin.repository.elasticsearch;

public interface VideoInfoDocExtensionRepository
{
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
