package com.neon.nilocommon.entity.constants;

/**
 * 主库 nilo 的表名<hr/>
 * 评论库的表名另建常量类，不要和这里混在一起
 */
public class NiloTable
{
    /**
     * 视频信息表
     */
    public static final String VIDEO_INFO = "video_info";

    /**
     * 视频分P文件表<hr/>
     * 只有审核通过时才会按 videoId 全删全插，平时不动，所以事件量很小
     */
    public static final String VIDEO_INFO_FILE = "video_info_file";

    /**
     * 用户信息表
     */
    public static final String USER_INFO = "user_info";
}
