package com.neon.nilocommon.util;


import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.RedisKey;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;


public class FfmpegUtil
{
    public static class VideoSize
    {
        private final int width;

        private final int height;

        public VideoSize(int width, int height)
        {
            this.width = width;
            this.height = height;
        }

        public int width()
        {
            return width;
        }

        public int height()
        {
            return height;
        }
    }

    /**
     * 创建缩略图
     *
     * @param srcPathStr 源图片地址
     * @param showLogs   是否显示ffmpeg的日志信息
     * @return 缩略图文件路径
     */
    public static String creatImgThumbnail(String srcPathStr, boolean showLogs)
    {
        String suffix = StringUtil.getSuffix(srcPathStr);
        String thumbnailPathStr = srcPathStr.substring(0, srcPathStr.lastIndexOf(".")) + RedisKey.THUMBNAIL_SUFFIX + suffix;

        String cmd = """
                ffmpeg.exe -i "%s" -vf scale=720:-1 "%s"
                """.formatted(srcPathStr, thumbnailPathStr);

        ProcessUtil.executeCommand(cmd, showLogs);

        return thumbnailPathStr;
    }

    /**
     * 获取视频时长
     *
     * @param videoPathStr 视频路径
     * @param showLogs     是否显示ffmpeg的日志信息
     * @return 视频时长（整数，单位：秒）
     */
    public static Integer getVideoDuration(String videoPathStr, boolean showLogs)
    {
        String cmd = """
                ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "%s"
                """.formatted(videoPathStr);

        String duration = ProcessUtil.executeCommand(cmd, showLogs);
        if (duration.isEmpty())
        {
            return null;
        }
        duration = duration.replace("\n", "");

        return new BigDecimal(duration).intValue();
    }

    /**
     * 检查文件是否包含视频流
     *
     * @param filePath 文件路径
     * @return true-包含视频流, false-不包含
     */
    public static boolean hasVideoStream(String filePath)
    {
        String cmd = """
                ffprobe -v error -select_streams v:0 -show_entries stream=codec_type -of default=noprint_wrappers=1:nokey=1 "%s"
                """.formatted(filePath);

        String result = ProcessUtil.executeCommand(cmd, false);

        return "video".equals(result.trim());
    }

    /**
     * 获取视频第一路视频流的宽高
     *
     * @param videoPathStr 视频路径
     * @return 视频宽高
     */
    public static VideoSize getVideoSize(String videoPathStr)
    {
        String cmd = """
                ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0:s=x "%s"
                """.formatted(videoPathStr);
        String result = ProcessUtil.executeCommand(cmd, false).trim();
        String[] parts = result.split("x");
        if (parts.length != 2)
        {
            throw new IllegalArgumentException("无法获取视频分辨率：" + videoPathStr);
        }
        return new VideoSize(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    /**
     * 将视频转换成Ts分片到指定目录
     *
     * @param srcPathStr      原文件路径
     * @param outputFolderStr 输出目录（该目录下会生成 index.ts、index.m3u8、tsFolder）
     * @param width           输出宽度
     * @param height          输出高度
     * @param bitrateKbps     视频码率（kbps）
     * @param showLogs        是否显示ffmpeg的日志信息
     */
    public static void convertVideoToTs(String srcPathStr,
                                        String outputFolderStr,
                                        int width,
                                        int height,
                                        int bitrateKbps,
                                        boolean showLogs) throws IOException
    {
        // 先创建输出文件夹
        Path outputFolder = Path.of(outputFolderStr);
        Files.createDirectories(outputFolder);

        // 组装并执行FFmpeg指令
        Path tsPath = outputFolder.toAbsolutePath().resolve(Constants.TS_NAME);
        String tsPathStr = tsPath.toString();
        String bitrate = bitrateKbps + "k";
        String bufferSize = bitrateKbps * 2 + "k";
        String videoFilter = "scale=%d:%d".formatted(width, height);
        // 将视频转换成指定格式的TS分片
        String cmd = """
                ffmpeg -y -i "%s" -vf "%s" -r 60 -c:v libx264 -b:v %s -maxrate %s -bufsize %s -c:a aac -b:a 128k -ar 44100 -ac 2 -pix_fmt yuv420p "%s"
                """.formatted(srcPathStr, videoFilter, bitrate, bitrate, bufferSize, tsPathStr);
        ProcessUtil.executeCommand(cmd, showLogs);

        // 创建ts文件保存文件夹
        String tsFolderPathStr = String.join("/", outputFolder.toAbsolutePath().toString(), Constants.TS_FOLDER_NAME);
        Files.createDirectories(Path.of(tsFolderPathStr));

        // 组装并执行FFmpeg指令
        String m3u8PathStr = String.join("/", outputFolder.toAbsolutePath().toString(), Constants.M3U8_NAME);
        // 公开直连 MinIO 时，index.m3u8 与 tsFolder 同级，使用相对路径 tsFolder/
        String segmentEntryPrefix = Constants.TS_FOLDER_NAME + "/";
        // 将TS分片，并生成m3u8
        cmd = """
                ffmpeg -i "%s" -c copy -map 0 -f segment -segment_list "%s" -segment_list_entry_prefix %s -segment_time 10 %s/%%4d.ts
                """.formatted(tsPathStr, m3u8PathStr, segmentEntryPrefix, tsFolderPathStr);
        ProcessUtil.executeCommand(cmd, showLogs);


        Files.deleteIfExists(tsPath);
    }
}
