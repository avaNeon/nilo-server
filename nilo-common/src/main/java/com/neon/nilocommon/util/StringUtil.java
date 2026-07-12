package com.neon.nilocommon.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class StringUtil
{

    /**
     * 校验文件路径是否合法且在指定根目录下
     *
     * @param safePathStr 允许的根目录
     * @param pathStr  待检查的路径字符串
     * @return true 合法, false 非法
     */
    public static boolean isValidPath(String safePathStr, String pathStr)
    {
        if (pathStr == null || pathStr.isEmpty()) return false;
        if (safePathStr == null || safePathStr.isEmpty()) return false;

        try
        {
            Path root = Path.of(safePathStr).toAbsolutePath().normalize();
            Path targetPath = Path.of(pathStr).toAbsolutePath().normalize();

            // 待检路径应该以指定路径打头
            return targetPath.startsWith(root);
        }
        catch (InvalidPathException | SecurityException e)
        {
            return false;
        }
    }

    /**
     * 尝试获取字符串的扩展名（带"."）
     *
     * @return 带"."的扩展名，如果没有"."则报错
     */
    public static String getSuffix(String str)
    {
        if (str == null || str.lastIndexOf(".") == -1) return null;
        return str.substring(str.lastIndexOf("."));
    }

    /**
     * 删除字符串的扩展名（最后一个"."及其后的部分）
     *
     * @return 不含扩展名的字符串；若无"."则返回原字符串；str 为 null 时返回 null
     */
    public static String removeSuffix(String str)
    {
        if (str == null)
        {
            return null;
        }
        int lastDotIndex = str.lastIndexOf(".");
        if (lastDotIndex == -1)
        {
            return str;
        }
        return str.substring(0, lastDotIndex);
    }

}
