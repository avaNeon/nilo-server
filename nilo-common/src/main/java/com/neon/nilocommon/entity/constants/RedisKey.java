package com.neon.nilocommon.entity.constants;

public class RedisKey
{

    /**
     * 所有Redis Key的前缀
     */
    private static final String REDIS_KEY_PREFIX = "nilo:";

    /**
     * 分类更新的分布式锁
     */
    public static final String CATEGORY_UPDATE_LOCK = REDIS_KEY_PREFIX + "category:update:lock";

    /**
     * redis中预上传视频标签的键名
     */
    public static final String PRE_UPLOADED_VIDEO_TAG_PREFIX = REDIS_KEY_PREFIX + "video:pre-uploaded-tag:";

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
     * 用户统计信息的分布式锁
     */
    public static final String USER_STATE_LOCK_PREFIX = REDIS_KEY_PREFIX + "user:state:lock:";

    /**
     * 用户统计信息前缀
     */
    public static final String USER_STATE_PREFIX = REDIS_KEY_PREFIX + "user:state:";

    /**
     * 验证码相关前缀
     */
    public static final String CAPTCHA_PREFIX = REDIS_KEY_PREFIX + "captcha:";

    /**
     * 视频在线统计 —— 心跳连接前缀
     */
    public static final String VIDEO_HEARTBEAT_PREFIX = REDIS_KEY_PREFIX + "video:heartbeat:";

    /**
     * 视频在线统计 —— 活跃视频列表前缀
     */
    public static final String VIDEO_ACTIVE_LIST = REDIS_KEY_PREFIX + "video:active-list";

    /**
     * 缩略图后缀
     */
    public static final String THUMBNAIL_SUFFIX = "_thumb";
}
