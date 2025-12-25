package com.neon.nilocommon.entity.constants;

public class Constants
{
    /* 常用常量 */
    /**
     * 用byte记的Mebibyte
     */
    public static final Long Mebibyte = 1024 * 1024L;

    /* 文件夹名相关 */
    /**
     * 总文件夹名
     */
    public static final String FILE_FOLDER_NAME = "file";
    /**
     * 封面文件夹名
     */
    public static final String COVER_FOLDER_NAME = "cover";
    /**
     * 视频文件夹名
     */
    public static final String VIDEO_FOLDER_NAME = "video";
    /**
     * 临时文件夹名
     */
    public static final String TMP_FOLDER_NAME = "tmp";

    /* 文件相关 */
    /**
     * 路径最大长度
     */
    public static final int MAX_PATH_LENGTH = 4096;
    /**
     * 文件名最大长度
     */
    public static final int MAX_FILENAME_LENGTH = 255;

    /* cookie key相关 */
    /**
     * cookie中web token键名
     */
    public static final String WEB_COOKIE_TOKEN_KEY = "token_normal";
    /**
     * cookie中admin token键名
     */
    public static final String ADMIN_COOKIE_TOKEN_KEY = "token_admin";

    /* 正则表达式 */
    /**
     * 密码的正则表达式<hr/>
     * 要求：<br/>
     * 8–20 位<br/>
     * 必须包含：数字 + 字母<br/>
     * 可包含符号：!@#$%^&*()_+-=<br/>
     */
    public static final String PASSWORD_REGEXP = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\-=]{8,20}$";

}
