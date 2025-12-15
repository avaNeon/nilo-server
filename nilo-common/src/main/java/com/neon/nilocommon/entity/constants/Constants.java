package com.neon.nilocommon.entity.constants;

public class Constants
{
    // 项目名称
    public static final String REDIS_KEY_PREFIX = "nilo:";
    // 验证码相关前缀
    public static final String REDIS_KEY_CAPTCHA = REDIS_KEY_PREFIX + "captcha:";

    // redis中web token前缀
    public static final String REDIS_TOKEN_WEB_PREFIX = REDIS_KEY_PREFIX + "token:web:";
    // cookie中web token键名
    public static final String COOKIE_TOKEN_WEB_KEY = "token_normal";
    // redis中admin token前缀
    public static final String REDIS_TOKEN_ADMIN_PREFIX = REDIS_KEY_PREFIX + "token:admin:";
    // cookie中admin token键名
    public static final String COOKIE_TOKEN_ADMIN_KEY = "token_admin";

    // redis中保存分类信息的键名
    public static final String REDIS_CATEGORIES_INFO_KEY = REDIS_KEY_PREFIX + "category:info";

    public static final String FILE_FOLDER_NAME = "file";

    public static final String COVER_FOLDER_NAME = "cover";

    public static final String VIDEO_FOLDER_NAME = "video";

    public static final String TMP_FOLDER_NAME = "tmp";

    public static final String REDIS_CATEGORY_UPDATE_LOCK = REDIS_KEY_PREFIX + "category:update:lock";

    // 缩略图前缀
    public static final String THUMBNAIL_SUFFIX = "_thumb";

    // 路径最大长度
    public static final int MAX_PATH_LENGTH = 4096;
    // 文件名最大长度
    public static final int MAX_FILENAME_LENGTH = 255;

    // 密码的正则表达式，要求：
    // 8–20 位
    // 必须包含：数字 + 字母
    // 可包含符号：!@#$%^&*()_+-=
    public static final String PASSWORD_REGEXP = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\-=]{8,20}$";
    // user_info表的主键user_id的长度
    public static final Integer USER_INFO_USER_ID_LENGTH = 10;
}
