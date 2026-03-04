package com.neon.nilocommon.util;


import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.RedisKey;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;


public class FFmpegUtil
{
    /**
     * 创建缩略图
     *
     * @param srcPathStr  源图片地址
     * @param showLogs 是否显示ffmpeg的日志信息
     */
    public static void creatImgThumbnail(String srcPathStr, boolean showLogs)
    {
        String cmd = "ffmpeg.exe -i \"%s\" -vf scale=200:-1 \"%s\"";
        String suffix = StringUtil.getSuffix(srcPathStr);
        cmd = String.format(cmd, srcPathStr, srcPathStr.substring(0, srcPathStr.lastIndexOf(".")) + RedisKey.THUMBNAIL_SUFFIX + suffix);
        ProcessUtil.executeCommand(cmd, showLogs);
    }

    /**
     * 获取视频时长
     *
     * @param videoPathStr 视频路径
     * @param showLogs  是否显示ffmpeg的日志信息
     * @return 视频时长（整数）
     */
    public static Integer getVideoDuration(String videoPathStr, boolean showLogs)
    {
        String cmd = "ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"%s\"";
        cmd = String.format(cmd, videoPathStr);
        String duration = ProcessUtil.executeCommand(cmd, showLogs);
        if (duration.isEmpty()) return null;
        duration = duration.replace("\n", "");
        return new BigDecimal(duration).intValue();
    }

    /**
     * 获取视频编码格式
     *
     * @param videoPathStr 视频路径
     * @param showLogs  showLogs 是否显示ffmpeg的日志信息
     * @return 视频编码格式
     */
    public static String getVideoEncoding(String videoPathStr, boolean showLogs)
    {
        String cmd = "ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 \"%s\"";
        cmd = String.format(cmd, videoPathStr);
        return ProcessUtil.executeCommand(cmd, showLogs);
    }

    /**
     * 将视频转换成H.264编码的mp4视频<hr/>
     * <li>原视频可以是任何编码格式的视频</li>
     * <li>如果目标路径存在文件，则会覆盖目标路径的文件</li>
     * <h4>注意：这个操作很耗时</h4>
     *
     * @param srcPathStr  原文件路径
     * @param destPathStr 新文件名称
     * @param showLogs 是否显示ffmpeg的日志信息
     */
    public static void convert2Mp4(String srcPathStr, String destPathStr, boolean showLogs)
    {
        String cmd = "ffmpeg -i \"%s\" -c:v libx264 -crf 20 -c:a aac -b:a 128k -ar 44100 -ac 2 -pix_fmt yuv420p -movflags +faststart \"%s\" -y";
        cmd = String.format(cmd, srcPathStr, destPathStr);
        ProcessUtil.executeCommand(cmd, showLogs);
    }

    /**
     * 将视频转换成Ts<hr/>
     * <li>原视频必须是H.264编码的mp4视频</li>
     * <li>生成的TS文件分片自动保存在原文件同目录下的tsFolder文件夹中</li>
     * @param srcPathStr  原文件路径
     * @param showLogs 是否显示ffmpeg的日志信息
     */
    public static void convertVideo2Ts(String srcPathStr, boolean showLogs) throws IOException
    {
        String cmd = "ffmpeg -y -i \"%s\" -c:v copy -c:a copy \"%s\"";
        Path parentFolder = Path.of(srcPathStr).getParent();
        String tsPathStr = parentFolder.toAbsolutePath() + "/" + Constants.TS_NAME;

        cmd = String.format(cmd, srcPathStr, tsPathStr);
        ProcessUtil.executeCommand(cmd, showLogs);

        String m3u8PathStr = parentFolder.toAbsolutePath() + "/" + Constants.M3U8_NAME;
        String tsFolderPathStr = parentFolder.toAbsolutePath() + "/" + Constants.TS_FOLDER_NAME;
        //先创建一个文件夹以防ffmpeg不会自动创建目录
        Files.createDirectories(Path.of(tsFolderPathStr));
        cmd = "ffmpeg -i \"%s\" -c copy -map 0 -f segment -segment_list \"%s\" -segment_time 10 %s/%%4d.ts";
        cmd = String.format(cmd, tsPathStr, m3u8PathStr, tsFolderPathStr);
        ProcessUtil.executeCommand(cmd, showLogs);
    }
}
