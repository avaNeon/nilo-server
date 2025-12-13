package com.neon.nilocommon.util;

import static com.neon.nilocommon.entity.constants.Constants.MAX_FILENAME_LENGTH;
import static com.neon.nilocommon.entity.constants.Constants.MAX_PATH_LENGTH;

public class StringUtil
{
    public static boolean isValidPath(String pathStr)
    {
        return isValidPath(pathStr, null);
    }

    public static boolean isValidPath(String pathStr, String rootPath)
    {
        if (pathStr == null || pathStr.isEmpty()) return false;
        if (pathStr.length() > MAX_PATH_LENGTH) return false;
        if (pathStr.contains("\0")) return false; // 空字符非法
        if (pathStr.contains("../") || pathStr.contains("/..")) return false; // 越权检查

        // 检查每段文件名长度
        String[] parts = pathStr.split("/");
        for (String part : parts)
        {
            if (part.length() > MAX_FILENAME_LENGTH) return false;
        }

        // 如果指定了根目录，路径必须以该根目录开头
        if (rootPath != null && !rootPath.isEmpty())
        {
            if (!pathStr.startsWith(rootPath)) return false;
        }

        return true;
    }

    /**
     * 尝试获取字符串的扩展名（带"."）
     * @return 带"."的扩展名，如果没有"."则报错
     */
    public static String getSuffix(String str)
    {
        if(str==null||str.lastIndexOf(".")==-1) return null;
        return str.substring(str.lastIndexOf("."));
    }
}
