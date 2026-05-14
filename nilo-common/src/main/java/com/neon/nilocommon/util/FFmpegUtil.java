package com.neon.nilocommon.util;


import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.constants.VideoResolution;

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
     */
    public static void creatImgThumbnail(String srcPathStr, boolean showLogs)
    {
        String suffix = StringUtil.getSuffix(srcPathStr);
        String cmd = """
                ffmpeg.exe -i "%s" -vf scale=200:-1 "%s"
                """.formatted(srcPathStr,
                              srcPathStr.substring(0, srcPathStr.lastIndexOf(".")) + RedisKey.THUMBNAIL_SUFFIX + suffix);
        ProcessUtil.executeCommand(cmd, showLogs);
    }

    /**
     * 获取视频时长
     *
     * @param videoPathStr 视频路径
     * @param showLogs     是否显示ffmpeg的日志信息
     * @return 视频时长（整数）
     */
    public static Integer getVideoDuration(String videoPathStr, boolean showLogs)
    {
        String cmd = """
                ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "%s"
                """.formatted(videoPathStr);
        String duration = ProcessUtil.executeCommand(cmd, showLogs);
        if (duration.isEmpty()) return null;
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
     * 将视频转换成Ts分片，转换为H.264编码、高度720px、60fps帧率、4Mbps码率，宽度按原比例自适应<hr/>
     * <li>生成的TS文件分片自动保存在原文件同目录下的tsFolder文件夹中</li>
     * <li>生成的m3u8索引文件保存在原文件同目录下</li>
     * <h4>注意：这个操作很耗时</h4>
     *
     * @param srcPathStr 原文件路径
     * @param showLogs   是否显示ffmpeg的日志信息
     */
    public static void convertVideo2Ts(String srcPathStr, boolean showLogs) throws IOException
    {
        Path parentFolder = Path.of(srcPathStr).getParent();
        VideoSize sourceSize = getVideoSize(srcPathStr);
        int width = sourceSize.width() * 720 / sourceSize.height();
        if (width % 2 != 0) width--;
        if (width < 2) width = 2;
        convertVideo2Ts(srcPathStr, parentFolder.toString(), width, 720, VideoResolution.RES_720P.getResolution(), 4000, showLogs);
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
    public static void convertVideo2Ts(String srcPathStr,
                                       String outputFolderStr,
                                       int width,
                                       int height,
                                       int resolution,
                                       int bitrateKbps,
                                       boolean showLogs) throws IOException
    {
        Path outputFolder = Path.of(outputFolderStr);
        Files.createDirectories(outputFolder);
        Path tsPath = outputFolder.toAbsolutePath().resolve(Constants.TS_NAME);
        String tsPathStr = tsPath.toString();
        String bitrate = bitrateKbps + "k";
        String bufferSize = bitrateKbps * 2 + "k";
        String videoFilter = "scale=%d:%d".formatted(width, height);
        String cmd = """
                ffmpeg -y -i "%s" -vf "%s" -r 60 -c:v libx264 -b:v %s -maxrate %s -bufsize %s -c:a aac -b:a 128k -ar 44100 -ac 2 -pix_fmt yuv420p "%s"
                """.formatted(srcPathStr, videoFilter, bitrate, bitrate, bufferSize, tsPathStr);
        ProcessUtil.executeCommand(cmd, showLogs);

        String m3u8PathStr = outputFolder.toAbsolutePath() + "/" + Constants.M3U8_NAME;
        String tsFolderPathStr = outputFolder.toAbsolutePath() + "/" + Constants.TS_FOLDER_NAME;
        Files.createDirectories(Path.of(tsFolderPathStr));
        String segmentEntryPrefix = "../segment/" + resolution + "/";
        cmd = """
                ffmpeg -i "%s" -c copy -map 0 -f segment -segment_list "%s" -segment_list_entry_prefix %s -segment_time 10 %s/%%4d.ts
                """.formatted(tsPathStr, m3u8PathStr, segmentEntryPrefix, tsFolderPathStr);
        ProcessUtil.executeCommand(cmd, showLogs);
        Files.deleteIfExists(tsPath);
    }
}
