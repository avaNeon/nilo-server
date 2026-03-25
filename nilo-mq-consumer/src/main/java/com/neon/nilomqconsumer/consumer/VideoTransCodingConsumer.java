package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.UploadedVideoFileDTO;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.VideoFileStatus;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.VideoInfoUpload;
import com.neon.nilocommon.entity.query.VideoInfoFileUploadQuery;
import com.neon.nilocommon.entity.query.VideoInfoUploadQuery;
import com.neon.nilocommon.util.FFmpegUtil;
import com.neon.nilocommon.util.VideoMergeUtils;
import com.neon.nilomqconsumer.mapper.VideoInfoFileUploadMapper;
import com.neon.nilomqconsumer.mapper.VideoInfoUploadMapper;
import com.neon.nilomqconsumer.repository.redis.TransCodingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
     * <li>3. 将文件合并、转码位TS文件</li>
     * <li>4. 将TS文件分割并生成m3u8</li>
     * <li>5. 将当前文件标记为转码成功，并查询所有该视频下文件转码信息</li>
     * <li>6. 若有视频文件转码失败，将视频文件状态标记位转码失败</li>
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
            UploadedVideoFileDTO fileDTO = transCodingRedisRepository.getPreUploadKey(fileUpload.getUserId(), fileUpload.getUploadId());
            if (fileDTO == null) throw new RuntimeException("未找到转码文件的记录");

            String from = Paths.get(rootPathStr, Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, fileDTO.getFilePath()).toString();
            Path srcPath = Path.of(from);
            String to = Paths.get(rootPathStr, Constants.FILE_FOLDER_NAME, Constants.VIDEO_FOLDER_NAME, fileDTO.getFilePath()).toString();
            Path destPath = Path.of(to);

            // 移动视频文件
            if (Files.exists(destPath)) FileUtils.deleteDirectory(destPath.toFile()); // 如果目标目录存在，那就直接删除避免报错
            FileUtils.moveDirectory(srcPath.toFile(), destPath.toFile());

            // 文件合并
            String completeVideoPath = to + "/" + Constants.TMP_VIDEO_NAME;
            VideoMergeUtils.mergeChunks(to, completeVideoPath, true);

            // 获取视频时长
            Integer duration = FFmpegUtil.getVideoDuration(completeVideoPath, false);
            if (duration == null)
            {
                throw new RuntimeException("无法获取视频时长");
            }

            // 设置视频文件相关信息
            fileUpload.setFileName(fileDTO.getFileName());
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
        Path videoPath = Path.of(videoPathStr);
        String encoding = FFmpegUtil.getVideoEncoding(videoPathStr, false);
        // 转化为h264编码格式
        if (!encoding.equals("h264"))
        {
            // 复制一个视频的临时文件，并将临时文件转化为h264编吗mp4文件并覆盖原文件
            String tmpVideoPathStr = videoPathStr + Constants.TMP_VIDEO_SUFFIX;
            Files.copy(videoPath, Path.of(tmpVideoPathStr));
            FFmpegUtil.convert2Mp4(tmpVideoPathStr, videoPathStr, false);
            Files.deleteIfExists(Path.of(tmpVideoPathStr));
        }
        FFmpegUtil.convertVideo2Ts(videoPathStr, false);
        Files.deleteIfExists(Path.of(videoPathStr)); // 删除掉这个tmp_video.mp4
    }

}
