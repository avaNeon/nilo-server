package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;


/**
 * 用于为用户请求上传视频key的行为加锁
 */
@Setter
@Getter
public class UserUploadVideoLock
{
    private Long userId;
}
