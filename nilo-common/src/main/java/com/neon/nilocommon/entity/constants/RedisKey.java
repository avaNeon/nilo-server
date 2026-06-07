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
     * <b>热门视频排行榜</b><hr/>
     * <p>这个排行榜中的视频将会展示给用户</p>
     */
    public static final String HOT_VIDEO_RANKING = REDIS_KEY_PREFIX + "{play-count}:ranking";

    /**
     * <b>热门视频时间记录前缀</b><hr/>
     */
    public static final String HOT_VIDEO_COUNTING_PREFIX = REDIS_KEY_PREFIX + "{play-count}:counting:";

    /**
     * <b>热门视频排行榜候选池</b><hr/>
     * <p>这个候选池用于存储还没达到热门播放量阈值的视频</p>
     */
    public static final String COLD_VIDEO_RANKING = REDIS_KEY_PREFIX + "{play-count}:cold-ranking";

    /**
     * <b>候选池视频时间记录前缀</b><hr/>
     */
    public static final String COLD_VIDEO_COUNTING_PREFIX = REDIS_KEY_PREFIX + "{play-count}:cold-counting:";

    /**
     * <b>视频历史播放记录统计（仅统计近2天）</b><hr/>
     */
    public static final String VIDEO_DAILY_PLAY_COUNT_PREFIX = REDIS_KEY_PREFIX + "{play-count}:daily:";
}
