package com.neon.nilocommon.entity.constants;

/**
 * <b>MQ相关常量定义</b><hr/>
 * <p>主要命名格式：&lt;业务分类&gt;_&lt;功能&gt;_&lt;MQ类型&gt;</p>
 * <p>如果是死信还会在前面加上特殊的DL前缀</p>
 */
public class MqInfo
{
    // ————STORAGE————————————————————————————————————————————————————————
    public static final String STORAGE_EXCHANGE = "storage.file.direct";
    // 视频文件删除
    public static final String STORAGE_VIDEO_DELETE_QUEUE = "storage.file.video.delete.queue";
    public static final String STORAGE_VIDEO_DELETE_ROUTING_KEY = "file.video.delete";
    // 图片文件删除
    public static final String STORAGE_IMAGE_DELETE_QUEUE = "storage.file.image.delete.queue";
    public static final String STORAGE_IMAGE_DELETE_ROUTING_KEY = "file.image.delete";
    // 视频转码
    public static final String STORAGE_TRANSCODING_QUEUE = "storage.file.transcoding.queue";
    public static final String STORAGE_TRANSCODING_ROUTING_KEY = "file.transcoding";

    public static final String STORAGE_DLX = "storage.file.dlx";
    // [死信]视频文件删除
    public static final String STORAGE_VIDEO_DELETE_DLQ = "storage.file.video.delete.dlq";
    public static final String STORAGE_VIDEO_DELETE_DLK = "file.video.delete.dead";
    // [死信]图片文件删除
    public static final String STORAGE_IMAGE_DELETE_DLQ = "storage.file.image.delete.dlq";
    public static final String STORAGE_IMAGE_DELETE_DLK = "file.image.delete.dead";
    // [死信]视频转码
    public static final String STORAGE_TRANSCODING_DLQ = "storage.file.transcoding.dlq";
    public static final String STORAGE_TRANSCODING_DLK = "file.transcoding.dead";

    // ————HEARTBEAT————————————————————————————————————————————————————————
    // 视频在线统计
    public static final String VIDEO_HEARTBEAT_EXCHANGE = "video.heartbeat.direct";
    public static final String VIDEO_HEARTBEAT_QUEUE = "video.heartbeat.queue";
    public static final String VIDEO_HEARTBEAT_ROUTING_KEY = "heartbeat.routing.key";

    // ————STATISTICS————————————————————————————————————————————————————————
    public static final String STATISTIC_EXCHANGE = "statistic.direct";
    public static final String STATISTIC_QUEUE = "statistic.queue";
    public static final String STATISTIC_ROUTING_KEY = "statistic.routing.key";
    // dlx
    public static final String STATISTIC_DLX = "statistic.dlx";
    public static final String STATISTIC_DLQ = "statistic.dlq";
    public static final String STATISTIC_DLK = "statistic.dead";

    // ————PLAY COUNT————————————————————————————————————————————————————————
    public static final String PLAY_COUNT_EXCHANGE = "video.play-count.direct";
    public static final String PLAY_COUNT_QUEUE = "video.play-count.queue";
    public static final String PLAY_COUNT_ROUTING_KEY = "video.play-count.routing.key";

    // ————COMMENT————————————————————————————————————————————————————————
    public static final String COMMENT_EXCHANGE = "comment.direct";
    public static final String COMMENT_UPDATE_QUEUE = "comment.update.queue";
    public static final String COMMENT_UPDATE_ROUTING_KEY = "comment.update";
    // 视频评论归档/恢复/彻底删除
    public static final String COMMENT_ARCHIVE_QUEUE = "comment.archive.queue";
    public static final String COMMENT_ARCHIVE_ROUTING_KEY = "comment.archive";
    // dlx
    public static final String COMMENT_DLX = "comment.dlx";
    public static final String COMMENT_UPDATE_DLQ = "comment.update.dlq";
    public static final String COMMENT_UPDATE_DLK = "comment.update.dead";
    // [死信]视频评论归档/恢复/彻底删除
    public static final String COMMENT_ARCHIVE_DLQ = "comment.archive.dlq";
    public static final String COMMENT_ARCHIVE_DLK = "comment.archive.dead";

    // ————EMAIL————————————————————————————————————————————————————————
    public static final String EMAIL_EXCHANGE = "email.direct";
    // 邮箱验证码发送
    public static final String EMAIL_SEND_QUEUE = "email.send.queue";
    public static final String EMAIL_SEND_ROUTING_KEY = "email.send";
    // dlx
    public static final String EMAIL_DLX = "email.dlx";
    public static final String EMAIL_SEND_DLQ = "email.send.dlq";
    public static final String EMAIL_SEND_DLK = "email.send.dead";

    // ————AI 索引————————————————————————————————————————————————————————
    public static final String AI_INDEX_EXCHANGE = "ai.index.direct";
    // 视频的标题标签简介向量，由 canal 监听 video_info 触发
    public static final String AI_VIDEO_INDEX_QUEUE = "ai.video.index.queue";
    public static final String AI_VIDEO_INDEX_ROUTING_KEY = "ai.video.index";
    // 分P字幕切块向量，由 canal 监听 video_info_file 触发。和上面分开是因为数据源是两张表，重建代价也差一个量级
    public static final String AI_SUBTITLE_INDEX_QUEUE = "ai.subtitle.index.queue";
    public static final String AI_SUBTITLE_INDEX_ROUTING_KEY = "ai.subtitle.index";
    // dlx
    public static final String AI_INDEX_DLX = "ai.index.dlx";
    public static final String AI_VIDEO_INDEX_DLQ = "ai.video.index.dlq";
    public static final String AI_VIDEO_INDEX_DLK = "ai.video.index.dead";
    public static final String AI_SUBTITLE_INDEX_DLQ = "ai.subtitle.index.dlq";
    public static final String AI_SUBTITLE_INDEX_DLK = "ai.subtitle.index.dead";
}
