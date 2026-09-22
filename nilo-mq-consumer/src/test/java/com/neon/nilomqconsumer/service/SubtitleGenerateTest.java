package com.neon.nilomqconsumer.service;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.enums.subtitle.SubtitleResult;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.VideoInfoUpload;
import com.neon.nilocommon.entity.query.VideoInfoFileUploadQuery;
import com.neon.nilocommon.entity.query.VideoInfoUploadQuery;
import com.neon.nilomqconsumer.mapper.VideoInfoFileUploadMapper;
import com.neon.nilomqconsumer.mapper.VideoInfoUploadMapper;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 给已经发布的视频补生成字幕<hr/>
 *
 * <p>MinIO 里只剩转码后的 HLS，源视频已经删了，所以源视频从本地目录取：先传到 MinIO 临时区，
 * 再像转码消费者那样从 MinIO 下载下来，然后只跑字幕这一段（抽音频 → 识别 → 大模型质检 → 生成 SRT → 外语翻译成中文），
 * 最后把字幕传到视频的 HLS 目录下，和 master.m3u8 同级。
 *
 * <p>源视频放 tmp/ 而不是 pending/：tmp/ 3 天自动过期，万一中途失败没删掉也不会留垃圾；
 * 放 pending/ 的话，残留的源文件会和以后挪回 pending/ 的 HLS 目录冲突。
 *
 * <p>会上传文件、调用收费接口，在 IDEA 里手动运行；打包时靠 manual 标签跳过。需要 Nacos、MySQL、MinIO 可用。
 * 关掉了 MQ 监听，免得测试进程去消费真实队列里的消息。
 */
@Tag("manual")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class SubtitleGenerateTest
{
    /**
     * 本地视频目录：每个子文件夹对应一个视频，文件夹名是数据库里的文件名或视频名
     */
    private static final Path LOCAL_VIDEO_ROOT = Path.of("C:/Users/Unitn/Videos/Download");

    /**
     * 一个文件夹里可能有好几个 mp4（比如同一视频的两个分P），按时长挑；误差超过这么多秒就当没找到
     */
    private static final int DURATION_TOLERANCE_SECONDS = 2;

    @Autowired
    private SubtitleService subtitleService;

    @Autowired
    private FileService fileService;

    @Autowired
    private LocalFileService localFileService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    @Autowired
    private VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    @Test
    void generateSubtitles() throws Exception
    {
        if (!Files.isDirectory(LOCAL_VIDEO_ROOT))
        {
            throw new IllegalStateException("本地视频目录不存在: " + LOCAL_VIDEO_ROOT);
        }

        Map <Long, String> videoNames = new HashMap <>();
        for (VideoInfoUpload video : videoInfoUploadMapper.selectList(new VideoInfoUploadQuery()))
        {
            videoNames.put(video.getVideoId(), video.getVideoName());
        }

        // baseKey -> 打印用的名称 / HLS 所在前缀 / 识别任务 id
        Map <String, String> displayNames = new HashMap <>();
        Map <String, String> hlsPrefixes = new HashMap <>();
        Map <String, String> taskIds = new LinkedHashMap <>();
        List <String> generated = new ArrayList <>();
        List <String> skipped = new ArrayList <>();
        List <String> failed = new ArrayList <>();

        // 第一步：逐个分P提交识别。识别在云端跑，先全部提交，再统一取结果，总耗时短很多
        for (VideoInfoFileUpload file : videoInfoFileUploadMapper.selectList(new VideoInfoFileUploadQuery()))
        {
            String baseKey = file.getFilePath();
            String videoName = videoNames.get(file.getVideoId());
            String name = videoName + " / P" + file.getFileIndex() + "「" + file.getFileName() + "」";
            if (baseKey == null || baseKey.isBlank())
            {
                skipped.add(name + "：数据库里没有文件路径");
                continue;
            }
            displayNames.put(baseKey, name);

            try
            {
                if (file.getDuration() == null)
                {
                    skipped.add(name + "：数据库里没有时长，无法和本地文件对应");
                    continue;
                }
                String hlsPrefix = findHlsPrefix(baseKey);
                if (hlsPrefix == null)
                {
                    skipped.add(name + "：MinIO 里找不到它的 HLS");
                    continue;
                }
                if (taskIds.containsKey(baseKey))
                {
                    skipped.add(name + "：和前面的分P使用了同一个文件路径");
                    continue;
                }
                Path localVideo = findLocalVideo(file, videoName);
                if (localVideo == null)
                {
                    skipped.add(name + "：本地没找到对应的视频文件");
                    continue;
                }

                String taskId = submitFromMinio(baseKey, localVideo);
                if (taskId == null)
                {
                    skipped.add(name + "：没有音轨");
                    localFileService.delete(localDir(baseKey).toString());
                    continue;
                }
                hlsPrefixes.put(baseKey, hlsPrefix);
                taskIds.put(baseKey, taskId);
                System.out.println("已提交：" + name + "，本地文件：" + localVideo.getFileName());
            }
            catch (Exception e)
            {
                failed.add(name + "：提交失败，" + e.getMessage());
                localFileService.delete(localDir(baseKey).toString());
            }
        }

        // 第二步：逐个取识别结果，生成字幕（质检、翻译都在里面）并同步到 HLS 目录
        for (Map.Entry <String, String> entry : taskIds.entrySet())
        {
            String baseKey = entry.getKey();
            String name = displayNames.get(baseKey);
            Path subtitlePath = localDir(baseKey).resolve(Constants.SUBTITLE_NAME);
            try
            {
                SubtitleResult result = subtitleService.writeSrt(entry.getValue(), subtitlePath);
                // 本地生成了哪些就传哪些，没生成的把 MinIO 上的旧文件删掉，免得上次跑出来的乱码字幕还留着
                String keyPrefix = hlsPrefixes.get(baseKey) + baseKey + "/";
                syncToMinio(subtitlePath, keyPrefix + Constants.SUBTITLE_NAME);
                syncToMinio(subtitlePath.resolveSibling(Constants.SUBTITLE_ZH_NAME), keyPrefix + Constants.SUBTITLE_ZH_NAME);

                if (result == SubtitleResult.NO_SPEECH || result == SubtitleResult.UNREADABLE)
                {
                    skipped.add(name + "：" + result.getDescription());
                    continue;
                }
                generated.add(name + "：" + result.getDescription());
                preview(name, subtitlePath);
                if (result == SubtitleResult.TRANSLATED)
                {
                    preview(name + "（中文翻译）", subtitlePath.resolveSibling(Constants.SUBTITLE_ZH_NAME));
                }
            }
            catch (Exception e)
            {
                failed.add(name + "：生成字幕失败，" + e.getMessage());
            }
            finally
            {
                localFileService.delete(localDir(baseKey).toString());
            }
        }

        System.out.println("\n===== 字幕生成结果 =====");
        System.out.println("生成 " + generated.size() + " 个：");
        generated.forEach(line -> System.out.println("  " + line));
        System.out.println("跳过 " + skipped.size() + " 个：");
        skipped.forEach(line -> System.out.println("  " + line));
        System.out.println("失败 " + failed.size() + " 个：");
        failed.forEach(line -> System.out.println("  " + line));

        assertTrue(failed.isEmpty(), "有分P生成字幕失败，原因见上面的汇总");
    }

    /**
     * 模拟转码消费者拿源文件的过程：本地视频先传到 MinIO 临时区，再下载到转码目录，然后提交识别<hr/>
     * MinIO 上的临时源文件用完就删
     *
     * @return 识别任务 id；视频没有音轨时返回 null
     */
    private String submitFromMinio(String baseKey, Path localVideo) throws Exception
    {
        String tmpKey = MinioKey.TMP_PREFIX + baseKey;
        minioClient.uploadObject(UploadObjectArgs.builder()
                                                 .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                 .object(tmpKey)
                                                 .filename(localVideo.toString())
                                                 .build());
        try
        {
            Path sourcePath = localDir(baseKey).resolve(Constants.SOURCE_VIDEO_NAME);
            fileService.downloadVideoFile(tmpKey, sourcePath);
            String taskId = subtitleService.submit(sourcePath);
            // 提交后源视频就用不上了，先删掉省磁盘
            Files.deleteIfExists(sourcePath);
            return taskId;
        }
        finally
        {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(MinioBucket.MINIO_VIDEO_BUCKET).object(tmpKey).build());
        }
    }

    /**
     * 本地有这个字幕文件就上传，没有就删掉 MinIO 上的同名文件（不存在也不报错）
     */
    private void syncToMinio(Path localFile, String key) throws Exception
    {
        if (Files.exists(localFile))
        {
            minioClient.uploadObject(UploadObjectArgs.builder()
                                                     .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                     .object(key)
                                                     .filename(localFile.toString())
                                                     // 设成文本，在 MinIO 控制台里能直接预览
                                                     .contentType("text/plain; charset=utf-8")
                                                     .build());
        }
        else
        {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(MinioBucket.MINIO_VIDEO_BUCKET).object(key).build());
        }
    }

    /**
     * 找这个分P的 HLS 现在在哪个前缀下：审核通过的在 public/，还没审核的在 pending/
     *
     * @return 前缀；两边都没有时返回 null
     */
    private String findHlsPrefix(String baseKey) throws Exception
    {
        for (String prefix : List.of(MinioKey.PUBLIC_PREFIX, MinioKey.PENDING_PREFIX))
        {
            try
            {
                minioClient.statObject(StatObjectArgs.builder()
                                                     .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                     .object(prefix + baseKey + "/" + Constants.MASTER_M3U8_NAME)
                                                     .build());
                return prefix;
            }
            catch (ErrorResponseException e)
            {
                if (!e.errorResponse().code().startsWith("NoSuch"))
                {
                    throw e;
                }
            }
        }
        return null;
    }

    /**
     * 在本地目录里找这个分P对应的视频文件<hr/>
     * <p>文件夹名对应数据库里的文件名或视频名（Windows 文件夹名不能以点结尾，比较时去掉结尾的点）。
     * 文件夹里有多个 mp4 时按时长挑最接近的。</p>
     *
     * @return 找不到时返回 null
     */
    private Path findLocalVideo(VideoInfoFileUpload file, String videoName) throws IOException, InterruptedException
    {
        String fileName = normalize(file.getFileName());
        String normalizedVideoName = normalize(videoName);
        Path best = null;
        int bestDiff = Integer.MAX_VALUE;
        try (DirectoryStream <Path> folders = Files.newDirectoryStream(LOCAL_VIDEO_ROOT, Files::isDirectory))
        {
            for (Path folder : folders)
            {
                String folderName = normalize(folder.getFileName().toString());
                if (!folderName.equals(fileName) && !folderName.equals(normalizedVideoName))
                {
                    continue;
                }
                try (DirectoryStream <Path> videos = Files.newDirectoryStream(folder))
                {
                    for (Path video : videos)
                    {
                        if (!video.getFileName().toString().toLowerCase().endsWith(".mp4"))
                        {
                            continue;
                        }
                        int diff = Math.abs(probeDuration(video) - file.getDuration());
                        if (diff < bestDiff)
                        {
                            best = video;
                            bestDiff = diff;
                        }
                    }
                }
            }
        }
        return bestDiff <= DURATION_TOLERANCE_SECONDS ? best : null;
    }

    /**
     * 读本地视频时长（秒）<hr/>
     * 不用 FfmpegUtil：它在 Windows 上按空白拆命令，路径里有连续两个空格（比如「You've Got Mail!  feat.」）会被并成一个，
     * 就找不到文件了。转码流程里的路径是 file/tmp/{baseKey}/source，没有空格，不受影响
     */
    private int probeDuration(Path video) throws IOException, InterruptedException
    {
        Process process = new ProcessBuilder("ffprobe",
                                             "-v",
                                             "error",
                                             "-show_entries",
                                             "format=duration",
                                             "-of",
                                             "default=noprint_wrappers=1:nokey=1",
                                             video.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        if (process.waitFor() != 0)
        {
            throw new IllegalStateException("读取视频时长失败, video=" + video + ", output=" + output);
        }
        return (int) Double.parseDouble(output);
    }

    private String normalize(String name)
    {
        return name == null ? "" : name.strip().replaceAll("[.\\s]+$", "");
    }

    /**
     * 和转码消费者用同一个本地目录：file/tmp/{baseKey}
     */
    private Path localDir(String baseKey)
    {
        return Path.of(Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, baseKey);
    }

    /**
     * 打印字幕前 5 条，方便直接在控制台看效果
     */
    private void preview(String name, Path subtitlePath) throws IOException
    {
        System.out.println("\n----- " + name + " -----");
        Files.readAllLines(subtitlePath).stream().limit(20).forEach(System.out::println);
    }
}
