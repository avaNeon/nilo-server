package com.neon.nilocommon.entity.enums.videoIndex;

/**
 * AI 向量索引要做的事
 */
public enum VideoIndexTaskType
{
    /**
     * 重建：视频发布，或者标题、标签、简介改了
     */
    UPSERT,

    /**
     * 删除：视频下架或删除
     */
    DELETE
}
