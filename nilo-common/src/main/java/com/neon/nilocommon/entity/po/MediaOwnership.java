package com.neon.nilocommon.entity.po;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文件归属表
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MediaOwnership
{
    /**
     * 自增主键
     */
    private Long id;

    /**
     * minio key（不保存前缀）
     */
    private String objectKey;

    /**
     * minio中的bucket
     */
    private String bucket;

    /**
     * 所有者用户ID
     */
    private Long ownerId;

    /**
     * 0表示未用，1表示已用
     */
    private Integer used;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 使用时间
     */
    private LocalDateTime usedTime;

}
