package com.neon.nilocommon.entity.enums.comment;

/**
 * 评论库副本表（video_info_replica、user_info_replica）要做的同步操作
 */
public enum ReplicaSyncType
{
    /**
     * 新增或覆盖：主库新增了这一行，或者同步的列改了
     */
    UPSERT,

    /**
     * 删除：主库删除了这一行（视频删除、用户注销）
     */
    DELETE
}
