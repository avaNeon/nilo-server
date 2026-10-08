package com.neon.nilocanalclient.entity;

import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import lombok.Getter;

/**
 * 一次 ES bulk 中的单条操作：Index 或 Delete 操作
 */
@Getter
public class VideoInfoDocBulkOpt
{
    private final Type type;

    private final Long videoId;

    /**
     * INDEX 时有值，DELETE 时为 null
     */
    private final VideoInfoDoc doc;

    /**
     * 私有构造函数，只允许使用静态方法创建实例
     */
    private VideoInfoDocBulkOpt(Type type, Long videoId, VideoInfoDoc doc)
    {
        this.type = type;
        this.videoId = videoId;
        this.doc = doc;
    }

    public static VideoInfoDocBulkOpt buildIndex(VideoInfoDoc doc)
    {
        return new VideoInfoDocBulkOpt(Type.INDEX, doc.getVideoId(), doc);
    }

    public static VideoInfoDocBulkOpt buildDelete(Long videoId)
    {
        return new VideoInfoDocBulkOpt(Type.DELETE, videoId, null);
    }
}
