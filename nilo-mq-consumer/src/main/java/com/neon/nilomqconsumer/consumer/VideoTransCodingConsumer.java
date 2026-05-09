package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.constants.VideoResolution;
import com.neon.nilocommon.entity.dto.UploadedVideoFileDTO;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.VideoFileStatus;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.VideoInfoUpload;
import com.neon.nilocommon.entity.query.VideoInfoFileUploadQuery;
import com.neon.nilocommon.entity.query.VideoInfoUploadQuery;
import com.neon.nilocommon.util.FFmpegUtil;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.VideoMergeUtils;
import com.neon.nilomqconsumer.mapper.VideoInfoFileUploadMapper;
import com.neon.nilomqconsumer.mapper.VideoInfoUploadMapper;
import com.neon.nilomqconsumer.repository.redis.TransCodingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class VideoTransCodingConsumer
{

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final TransCodingRedisRepository transCodingRedisRepository;

    @Value("${project.folder}")
    private String rootPathStr;

    /**
     * 消费转码视频文件的任务<hr/>
     * 任务流程：<br/>
     * <li>1. 从redis查询转码文件的记录，获取文件地址</li>
     * <li>2. 将文件从临时目录转移到视频目录</li>
     * <li>3. 将文件合并、转码为TS文件</li>
     * <li>4. 将TS文件分割并生成m3u8</li>
     * <li>5. 将当前文件标记为转码成功，并查询所有该视频下文件转码信息</li>
     * <li>6. 若有视频文件转码失败，将视频文件状态标记为转码失败</li>
     * <li>7. 若所有视频文件转码成功，计算视频总时长并将视频标记为待审核状态</li>
     * <hr/>
     * 填写了 VideoInfoFileUpload 的file_name, file_size, file_path, duration, transfer_result这几个字段
     *
     * @param fileUpload 视频文件
     */
    @RabbitListener(queues = MqInfo.STORAGE_TRANSCODING_QUEUE)
    public void receiveMessage(VideoInfoFileUpload fileUpload)
    {
        try
        {
            // 先转换视频封面
            VideoInfoUpload videoInfoUpload = videoInfoUploadMapper.selectByVideoId(fileUpload.getVideoId());
            FileUtil.verifyAndMoveCover(rootPathStr, videoInfoUpload.getVideoCover());

            UploadedVideoFileDTO fileDTO = transCodingRedisRepository.getPreUploadKey(fileUpload.getUserId(),
                                                                                      fileUpload.getUploadId());
            if (fileDTO == null) throw new RuntimeException("未找到转码文件的记录");

            String from = Paths.get(rootPathStr, Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, fileDTO.getFilePath())
                               .toString();
            Path srcPath = Path.of(from);
            String to = Paths.get(rootPathStr, Constants.FILE_FOLDER_NAME, Constants.VIDEO_FOLDER_NAME, fileDTO.getFilePath())
                             .toString();
            Path destPath = Path.of(to);

            // 移动视频文件
            if (Files.exists(destPath)) FileUtils.deleteDirectory(destPath.toFile()); // 如果目标目录存在，那就直接删除避免报错
            FileUtils.moveDirectory(srcPath.toFile(), destPath.toFile());

            // 文件合并
            String completeVideoPath = to + "/" + Constants.TMP_VIDEO_NAME;
            VideoMergeUtils.mergeChunks(to, completeVideoPath, true);

            // 检查合并后的文件是否包含视频流
            if (!FFmpegUtil.hasVideoStream(completeVideoPath))
            {
                throw new RuntimeException("文件不包含视频流，无法转码");
            }

            // 获取视频时长
            Integer duration = FFmpegUtil.getVideoDuration(completeVideoPath, false);
            if (duration == null)
            {
                throw new RuntimeException("无法获取视频时长");
            }

            // 设置视频文件相关信息
            fileUpload.setDuration(duration);
            fileUpload.setFileSize(new File(completeVideoPath).length());
            fileUpload.setFilePath(Constants.VIDEO_FOLDER_NAME + "/" + fileDTO.getFilePath());

            // 将视频转换为分段TS文件（tsFolder/xxxx.ts 不足4位会补0补到4位）和m3u8（index.m3u8）
            convertVideo2Ts(completeVideoPath);

            fileUpload.setTransferResult(VideoFileStatus.TRANSCODING_SUCCESS.getStatus());

            // 删除在redis的记录
            transCodingRedisRepository.deletePreUploadKey(fileUpload.getUserId(), fileUpload.getUploadId());
        }
        catch (Exception e)
        {
            fileUpload.setTransferResult(VideoFileStatus.TRANSCODING_FAIL.getStatus());
            throw new RuntimeException("视频转码失败", e);
        }
        finally
        {
            // 将对VideoInfoFileUpload的修改保存到MySQL
            videoInfoFileUploadMapper.updateByUploadIdAndUserId(fileUpload, fileUpload.getUploadId(), fileUpload.getUserId());
            VideoInfoFileUploadQuery query = new VideoInfoFileUploadQuery();
            query.setVideoId(fileUpload.getVideoId());
            query.setTransferResult(VideoFileStatus.TRANSCODING_FAIL.getStatus());
            Integer result = videoInfoFileUploadMapper.selectCount(query);
            // 如果有文件转码失败
            if (result != null && result > 0)
            {
                VideoInfoUpload videoInfoUpload = new VideoInfoUpload();
                videoInfoUpload.setStatus(VideoStatus.TRANSCODING_FAIL.getStatus());
                videoInfoUploadMapper.updateByVideoId(videoInfoUpload, fileUpload.getVideoId());
            }
            else // 如果没有文件转码失败
            {
                // 再检查是否有文件处于转码中
                query.setTransferResult(VideoFileStatus.TRANSCODING.getStatus());
                result = videoInfoFileUploadMapper.selectCount(query);
                // 如果所有文件都转码成功
                if (result != null && result == 0)
                {
                    Integer totalDuration = videoInfoFileUploadMapper.sumDuration(fileUpload.getVideoId());
                    VideoInfoUpload videoInfoUpload = new VideoInfoUpload();
                    videoInfoUpload.setStatus(VideoStatus.PENDING_REVIEW.getStatus());
                    videoInfoUpload.setDuration(totalDuration);
                    videoInfoUploadMapper.updateByVideoId(videoInfoUpload, fileUpload.getVideoId());
                }
            }

        }
    }

    /**
     * 将视频转换为TS分片文件
     *
     * @param videoPathStr 原视频路径
     * @throws IOException 由于涉及储存，可能产生IO异常
     */
    private void convertVideo2Ts(String videoPathStr) throws IOException
    {
        String parentPath = Path.of(videoPathStr).getParent().toString();
        FFmpegUtil.VideoSize sourceSize = FFmpegUtil.getVideoSize(videoPathStr);
        List <HlsVariant> variants = selectHlsVariants(sourceSize);

        for (HlsVariant variant : variants)
        {
            String outputPath = Paths.get(parentPath, variant.folderName()).toString();
            FFmpegUtil.convertVideo2Ts(videoPathStr,
                                       outputPath,
                                       variant.width(),
                                       variant.height(),
                                       variant.resolution(),
                                       variant.videoBitrateKbps(),
                                       false);
        }

        writeMasterM3u8(Path.of(parentPath), variants);
        Files.deleteIfExists(Path.of(videoPathStr));
    }

    private List <HlsVariant> selectHlsVariants(FFmpegUtil.VideoSize sourceSize)
    {
        List <HlsVariant> variants = new ArrayList <>();
        if (sourceSize.height() >= 720)
        {
            variants.add(new HlsVariant(VideoResolution.RES_720P.getFolderName(),
                                        1280,
                                        720,
                                        VideoResolution.RES_720P.getResolution(),
                                        4000,
                                        4000000,
                                        3500000,
                                        "avc1.64001f"));
        }

        if (sourceSize.height() >= 480)
        {
            variants.add(new HlsVariant(VideoResolution.RES_480P.getFolderName(),
                                        854,
                                        480,
                                        VideoResolution.RES_480P.getResolution(),
                                        1600,
                                        1600000,
                                        1400000,
                                        "avc1.4d401f"));
        }
        else
        {
            variants.add(new HlsVariant(VideoResolution.RES_480P.getFolderName(),
                                        evenSize(sourceSize.width()),
                                        evenSize(sourceSize.height()),
                                        VideoResolution.RES_480P.getResolution(),
                                        1600,
                                        1600000,
                                        1400000,
                                        "avc1.4d401f"));
        }

        return variants;
    }

    private int evenSize(int value)
    {
        if (value <= 2) return 2;
        return value % 2 == 0 ? value : value - 1;
    }

    private void writeMasterM3u8(Path parentPath, List <HlsVariant> variants) throws IOException
    {
        StringBuilder content = new StringBuilder("""
                                                  #EXTM3U
                                                  #EXT-X-VERSION:3
                                                  #EXT-X-INDEPENDENT-SEGMENTS
                                                  """);
        for (HlsVariant variant : variants)
        {
            content.append("""
                           #EXT-X-STREAM-INF:BANDWIDTH=%d,AVERAGE-BANDWIDTH=%d,RESOLUTION=%dx%d,CODECS="%s,mp4a.40.2"
                           playlist/%d.m3u8
                           """.formatted(variant.bandwidth(),
                                          variant.averageBandwidth(),
                                          variant.width(),
                                          variant.height(),
                                          variant.videoCodec(),
                                          variant.resolution()));
        }
        Files.writeString(parentPath.resolve(Constants.MASTER_M3U8_NAME), content.toString(), StandardCharsets.UTF_8);
    }

    private static class HlsVariant
    {
        private final String folderName;

        private final int width;

        private final int height;

        private final int resolution;

        private final int videoBitrateKbps;

        private final int bandwidth;

        private final int averageBandwidth;

        private final String videoCodec;

        private HlsVariant(String folderName,
                           int width,
                           int height,
                           int resolution,
                           int videoBitrateKbps,
                           int bandwidth,
                           int averageBandwidth,
                           String videoCodec)
        {
            this.folderName = folderName;
            this.width = width;
            this.height = height;
            this.resolution = resolution;
            this.videoBitrateKbps = videoBitrateKbps;
            this.bandwidth = bandwidth;
            this.averageBandwidth = averageBandwidth;
            this.videoCodec = videoCodec;
        }

        private String folderName()
        {
            return folderName;
        }

        private int width()
        {
            return width;
        }

        private int height()
        {
            return height;
        }

        private int resolution()
        {
            return resolution;
        }

        private int videoBitrateKbps()
        {
            return videoBitrateKbps;
        }

        private int bandwidth()
        {
            return bandwidth;
        }

        private int averageBandwidth()
        {
            return averageBandwidth;
        }

        private String videoCodec()
        {
            return videoCodec;
        }
    }

}
