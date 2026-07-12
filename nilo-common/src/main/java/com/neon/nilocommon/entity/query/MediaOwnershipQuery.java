package com.neon.nilocommon.entity.query;

import lombok.Getter;
import lombok.Setter;


/**
 * 参数
 */
@Setter
@Getter
public class MediaOwnershipQuery extends BaseQuery
{
    /**
     * 自增主键
     */
    private Long id;

    /**
     * minio key
     */
    private String objectKey;

    private String objectKeyFuzzy;

    /**
     * minio中的bucket
     */
    private String bucket;

    private String bucketFuzzy;

    /**
     * 所有者用户ID
     */
    private Long ownerId;

    /**
     * 0表示已经用过，1表示尚未用过
     */
    private Integer used;

    /**
     * 创建时间
     */
    private String createdTime;

    private String createdTimeStart;

    private String createdTimeEnd;

    /**
     * 使用时间
     */
    private String usedTime;

    private String usedTimeStart;

    private String usedTimeEnd;
}
