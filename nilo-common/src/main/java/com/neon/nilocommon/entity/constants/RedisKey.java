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
     * 用户每日上传额度前缀<hr/>
     * <p>key: nilo:upload:quota:{image|video}:size:{userId}:{yyyyMMdd}</p>
     */
    public static final String UPLOAD_QUOTA_PREFIX = REDIS_KEY_PREFIX + "upload:quota:";

    /**
     * 系统配置
     */
    public static final String SYSTEM_CONFIG = REDIS_KEY_PREFIX + "system:config";

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
     * 用户状态信息前缀
     */
    public static final String USER_AUTH_PREFIX = REDIS_KEY_PREFIX + "user:auth:";

    /**
     * 用户状态信息的分布式锁
     */
    public static final String USER_AUTH_LOCK_PREFIX = REDIS_KEY_PREFIX + "user:auth:lock:";

    /**
     * 用户统计信息前缀
     */
    public static final String USER_STATE_PREFIX = REDIS_KEY_PREFIX + "user:state:";

    /**
     * 用户统计信息的分布式锁
     */
    public static final String USER_STATE_LOCK_PREFIX = REDIS_KEY_PREFIX + "user:state:lock:";

    /**
     * 验证码相关前缀
     */
    public static final String CAPTCHA_PREFIX = REDIS_KEY_PREFIX + "captcha:";

    /**
     * 登录密码错误次数前缀<hr/>
     * <p>key: nilo:login:fail:{email}:{ip}</p>
     */
    public static final String LOGIN_FAILURE_PREFIX = REDIS_KEY_PREFIX + "login:fail:";

    /**
     * 视频在线统计 —— 心跳连接前缀
     */
    public static final String VIDEO_HEARTBEAT_PREFIX = REDIS_KEY_PREFIX + "{video-online}:heartbeat:";

    /**
     * 视频在线统计 —— 活跃视频列表的键名
     */
    public static final String VIDEO_ACTIVE_LIST = REDIS_KEY_PREFIX + "{video-online}:active-list";

    /**
     * 缩略图后缀
     */
    public static final String THUMBNAIL_SUFFIX = "_thumb";

    /**
     * 热词每日榜单键名前缀
     */
    public static final String HOT_KEYWORD_RANKING = REDIS_KEY_PREFIX + "hot-keyword:ranking";

    /**
     * 热门视频排行榜<hr/>
     * <p>这个排行榜中的视频将会展示给用户</p>
     */
    public static final String HOT_VIDEO_RANKING = REDIS_KEY_PREFIX + "{play-count}:ranking";

    /**
     * 热门视频时间记录前缀
     */
    public static final String HOT_VIDEO_COUNTING_PREFIX = REDIS_KEY_PREFIX + "{play-count}:counting:";

    /**
     * 热门视频排行榜候选池<hr/>
     * <p>这个候选池用于存储还没达到热门播放量阈值的视频</p>
     */
    public static final String COLD_VIDEO_RANKING = REDIS_KEY_PREFIX + "{play-count}:cold-ranking";

    /**
     * 候选池视频时间记录前缀
     */
    public static final String COLD_VIDEO_COUNTING_PREFIX = REDIS_KEY_PREFIX + "{play-count}:cold-counting:";

    /**
     * 视频历史播放记录统计（仅统计近2天）
     */
    public static final String VIDEO_DAILY_PLAY_COUNT_PREFIX = REDIS_KEY_PREFIX + "{play-count}:daily:";

    /**
     * 预签名URL缓存Key前缀
     */
    public static final String PRESIGNED_URL_CACHE_PREFIX = REDIS_KEY_PREFIX + "presigned:";

    /**
     * 邮箱验证码前缀<hr/>
     * <p>key: nilo:email:code:{scene}:{email}</p>
     */
    public static final String EMAIL_CODE_PREFIX = REDIS_KEY_PREFIX + "email:code:";

    /**
     * 邮箱验证码剩余可尝试次数前缀<hr/>
     * <p>key: nilo:email:code:attempts:{scene}:{email}</p>
     */
    public static final String EMAIL_CODE_ATTEMPTS_PREFIX = REDIS_KEY_PREFIX + "email:code:attempts:";

    /**
     * 邮箱验证码发送冷却前缀（防止连续重复发送）<hr/>
     * <p>key: nilo:email:cooldown:{scene}:{email}</p>
     */
    public static final String EMAIL_CODE_COOLDOWN_PREFIX = REDIS_KEY_PREFIX + "email:cooldown:";

    /**
     * 邮箱验证码发送频次前缀（固定窗口限流）<hr/>
     * <p>key: nilo:email:freq:{scene}:{email}</p>
     */
    public static final String EMAIL_CODE_FREQ_PREFIX = REDIS_KEY_PREFIX + "email:freq:";
}
