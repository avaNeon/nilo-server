package com.neon.nilocommon.entity.constants;

public class MinioKey
{
    /**
     * 临时文件前缀<hr/>
     * 3天后自动过期，任何人禁止查看
     */
    public static final String TMP_PREFIX = "tmp/";

    /**
     * 待处理文件前缀<hr/>
     * 私有文件，只有文件属主和管理员可以查看
     */
    public static final String PENDING_PREFIX = "pending/";

    /**
     * 公共文件前缀<hr/>
     * 公共文件，任何人可以无需权限直接查看
     */
    public static final String PUBLIC_PREFIX = "public/";
}
