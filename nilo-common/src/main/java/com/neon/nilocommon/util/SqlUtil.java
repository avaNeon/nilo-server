package com.neon.nilocommon.util;

public final class SqlUtil
{
    public static String escapeLike(String value)
    {
        if (value == null)
        {
            return null;
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
