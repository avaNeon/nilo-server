package com.neon.nilocommon.util;


import com.neon.nilocommon.entity.constants.RedisKey;


public class FFmpegUtil
{
    public static void creatImgThumbnail(String srcPath, boolean showLogs)
    {
        String cmd = "ffmpeg.exe -i \"%s\" -vf scale=200:-1 \"%s\"";
        String suffix = StringUtil.getSuffix(srcPath);
        cmd = String.format(cmd, srcPath, srcPath.substring(0, srcPath.lastIndexOf(".")) + RedisKey.THUMBNAIL_SUFFIX + suffix);
        ProcessUtil.executeCommand(cmd, showLogs);
    }
}
