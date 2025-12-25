package com.neon.nilocommon.entity.constants;

public class RedisKey
{

    /**
     * 项目名称
     */
    public static final String REDIS_KEY_PREFIX = "nilo:";

    /**
     * 分类更新的分布式锁
     */
    public static final String CATEGORY_UPDATE_LOCK = REDIS_KEY_PREFIX + "category:update:lock";

    /**
     * redis中上传视频的键名
     */
    public static final String UPLOADING_VIDEO_PREFIX = REDIS_KEY_PREFIX + "uploading-video:";

    /**
     * redis中保存分类信息的键名
     */
    public static final String CATEGORIES_INFO = REDIS_KEY_PREFIX + "category:info";

    /**
     * redis中admin token前缀
     */
    public static final String ADMIN_TOKEN_PREFIX = REDIS_KEY_PREFIX + "token:admin:";

    /**
     * redis中web token前缀
     */
    public static final String WEB_TOKEN_PREFIX = REDIS_KEY_PREFIX + "token:web:";

    /**
     * 验证码相关前缀
     */
    public static final String CAPTCHA_PREFIX = REDIS_KEY_PREFIX + "captcha:";

    /**
     * 缩略图后缀
     */
    public static final String THUMBNAIL_SUFFIX = "_thumb";
}
