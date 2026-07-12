package com.neon.nilocanalclient.entity;

import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import lombok.Getter;

/**
 * 一次 ES bulk 中的单条操作：toIndex 或 toDelete
 */
@Getter
public class BulkDocOperation
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
    private BulkDocOperation(Type type, Long videoId, VideoInfoDoc doc)
    {
        this.type = type;
        this.videoId = videoId;
        this.doc = doc;
    }

    public static BulkDocOperation toIndex(VideoInfoDoc doc)
    {
        return new BulkDocOperation(Type.INDEX, doc.getVideoId(), doc);
    }

    public static BulkDocOperation toDelete(Long videoId)
    {
        return new BulkDocOperation(Type.DELETE, videoId, null);
    }
}
