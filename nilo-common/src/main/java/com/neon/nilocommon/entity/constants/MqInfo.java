package com.neon.nilocommon.entity.constants;

/**
 * MQ相关常量定义<hr/>
 * 主要命名格式：&lt;业务分类&gt;_&lt;功能&gt;_&lt;MQ类型&gt; <br/>
 * 如果是死信还会在前面加上特殊的DL前缀
 */
public class MqInfo
{
    public static final String STORAGE_EXCHANGE = "storage.file.direct";
    // 文件删除
    public static final String STORAGE_DELETE_QUEUE = "storage.file.delete.queue";
    public static final String STORAGE_DELETE_ROUTING_KEY = "file.delete";
    // 视频转码
    public static final String STORAGE_TRANSCODING_QUEUE = "storage.file.transcoding.queue";
    public static final String STORAGE_TRANSCODING_ROUTING_KEY = "file.transcoding";

    public static final String DLX_EXCHANGE = "storage.file.dlx";
    // [死信]文件删除
    public static final String DLQ_STORAGE_DELETE_QUEUE = "storage.file.delete.dlq";
    public static final String DLQ_STORAGE_DELETE_ROUTING_KEY = "file.delete.dead";
    // [死信]视频转码
    public static final String DLQ_STORAGE_TRANSCODING_QUEUE = "storage.file.transcoding.dlq";
    public static final String DLQ_STORAGE_TRANSCODING_ROUTING_KEY = "file.transcoding.dead";
}