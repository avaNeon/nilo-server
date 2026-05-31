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
import com.neon.nilocommon.util.FfmpegUtil;
import com.neon.nilocommon.util.VideoMergeUtils;
import com.neon.nilomqconsumer.mapper.VideoInfoFileUploadMapper;
import com.neon.nilomqconsumer.mapper.VideoInfoUploadMapper;
import com.neon.nilomqconsumer.repository.redis.TransCodingRedisRepository;
import com.neon.nilomqconsumer.service.FileDeleteService;
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
import java.nio.file.InvalidPathException;
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

    private final FileDeleteService fileDeleteService;

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

        UploadedVideoFileDTO fileDTO = transCodingRedisRepository.getPreUploadKey(fileUpload.getUserId(),
                                                                                  fileUpload.getUploadId());
        try
        {
            if (fileDTO == null)
            {
                throw new RuntimeException("未找到转码文件的记录");
            }

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
            if (!FfmpegUtil.hasVideoStream(completeVideoPath))
            {
                throw new RuntimeException("文件不包含视频流，无法转码");
            }

            // 获取视频时长
            Integer duration = FfmpegUtil.getVideoDuration(completeVideoPath, false);
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
            // 转码失败的文件，其文件要么在TMP路径，要么在VIDEO路径，前者不用管，后者只要在 video_upload_file 中有记录，DB中记录在用户再次提交时删除，文件现在就删除
            // 幂等删除，因为有3次重试，我们不删除redis中的记录，但是把目标地址的文件删除，这样后续重试也有原文件（如果不是原文件异常的情况）
            // 必须是同步删除，否则可能出现刚产生新文件就被删除的情况
            try
            {
                if (fileDTO != null)
                {
                    String to = Paths.get(rootPathStr,
                                          Constants.FILE_FOLDER_NAME,
                                          Constants.VIDEO_FOLDER_NAME,
                                          fileDTO.getFilePath()).toString();
                    fileDeleteService.delete(to);
                }
            }
            catch (InvalidPathException invalidPathException)
            {
                // 文件路径不合法可能是由于 video_info_file_upload 存了脏数据，便于排错
                log.warn("文件路径不合法：{}，异常信息：{}", fileDTO, invalidPathException.toString());
            }
            catch (Exception innerException)
            {
                // 删除文件失败在日志中记录一下，避免硬盘存脏文件
                log.error("文件删除失败！文件路径：{}，异常信息:{}",
                          Paths.get(rootPathStr, Constants.FILE_FOLDER_NAME, Constants.VIDEO_FOLDER_NAME, fileDTO.getFilePath()),
                          innerException.toString());
            }
            throw new RuntimeException("视频转码失败", e);
        }
        finally
        {
            // 将对VideoInfoFileUpload的修改保存到MySQL
            videoInfoFileUploadMapper.updateByUploadIdAndUserId(fileUpload, fileUpload.getUploadId(), fileUpload.getUserId());
            // 查询是否有文件转码失败
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
        FfmpegUtil.VideoSize sourceSize = FfmpegUtil.getVideoSize(videoPathStr);
        List <HlsVariant> variants = selectHlsVariants(sourceSize);

        for (HlsVariant variant : variants)
        {
            String outputPath = Paths.get(parentPath, variant.folderName()).toString();
            FfmpegUtil.convertVideo2Ts(videoPathStr,
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

    private List <HlsVariant> selectHlsVariants(FfmpegUtil.VideoSize sourceSize)
    {
        List <HlsVariant> variants = new ArrayList <>();
        if (sourceSize.height() >= 720)
        {
            int width = evenSize(sourceSize.width() * 720 / sourceSize.height());
            variants.add(new HlsVariant(VideoResolution.RES_720P.getFolderName(),
                                        width,
                                        720,
                                        VideoResolution.RES_720P.getResolution(),
                                        4000,
                                        4000000,
                                        3500000,
                                        "avc1.64001f"));
        }

        if (sourceSize.height() >= 480)
        {
            int width = evenSize(sourceSize.width() * 480 / sourceSize.height());
            variants.add(new HlsVariant(VideoResolution.RES_480P.getFolderName(),
                                        width,
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
