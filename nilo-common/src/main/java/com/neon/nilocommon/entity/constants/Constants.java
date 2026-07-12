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
     * 临时文件夹名
     */
    public static final String TMP_FOLDER_NAME = "tmp";


    /* 文件名相关 */

    /**
     * 从minio提取的原始视频文件名称
     */
    public static final String SOURCE_VIDEO_NAME = "source";

    public static final String TS_NAME = "index.ts";

    public static final String M3U8_NAME = "index.m3u8";

    public static final String MASTER_M3U8_NAME = "master.m3u8";

    public static final String TS_FOLDER_NAME = "tsFolder";

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
