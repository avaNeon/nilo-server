package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.constants.VideoResolution;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.UpdateType;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.VideoFileStatus;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.MediaOwnership;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.VideoInfoUpload;
import com.neon.nilocommon.entity.query.MediaOwnershipQuery;
import com.neon.nilocommon.entity.query.VideoInfoFileUploadQuery;
import com.neon.nilocommon.entity.query.VideoInfoUploadQuery;
import com.neon.nilocommon.util.FfmpegUtil;
import com.neon.nilomqconsumer.feign.storage.InnerVideoFileFeignClient;
import com.neon.nilomqconsumer.mapper.MediaOwnershipMapper;
import com.neon.nilomqconsumer.mapper.VideoInfoFileUploadMapper;
import com.neon.nilomqconsumer.mapper.VideoInfoUploadMapper;
import com.neon.nilomqconsumer.service.FileService;
import com.neon.nilomqconsumer.service.LocalFileService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class VideoTransCodingConsumer
{
    private final FileService fileService;

    private final LocalFileService localFileService;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final InnerVideoFileFeignClient innerVideoFileFeignClient;

    private final MediaOwnershipMapper <MediaOwnership, MediaOwnershipQuery> mediaOwnershipMapper;

    /**
     * 消费转码视频文件的任务<hr/>
     * 任务流程：<br/>
     * <ol>
     *      <li>从MinIO查询转码文件的记录，获取文件地址</li>
     *      <li>将文件合并、转码为TS文件</li>
     *      <li>将TS文件分割并生成m3u8</li>
     *      <li>将当前文件标记为转码成功，并查询所有该视频下文件转码信息</li>
     *      <li>若有视频文件转码失败，将视频文件状态标记为转码失败</li>
     *      <li>若所有视频文件转码成功，计算视频总时长并将视频标记为待审核状态，再将所有分P上传至MinIO并删除原始源文件</li>
     * </ol>
     * <p>填写了 VideoInfoFileUpload 的file_name, file_size, file_path, duration, transfer_result这几个字段</p>
     *
     * @param fileUpload 视频文件
     */
    @RabbitListener(queues = MqInfo.STORAGE_TRANSCODING_QUEUE)
    public void receiveMessage(VideoInfoFileUpload fileUpload)
    {
        String baseKey = fileUpload.getFilePath();
        String key = MinioKey.PENDING_PREFIX + baseKey;
        Path localPath = Path.of(Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, baseKey, Constants.SOURCE_VIDEO_NAME);

        try
        {
            // 先把文件从minio下载到本地
            fileService.downloadVideoFile(key, localPath);

            // 检查合并后的文件是否包含视频流
            if (!FfmpegUtil.hasVideoStream(localPath.toString()))
            {
                throw new RuntimeException("文件不包含视频流，无法转码");
            }

            // 获取视频时长
            Integer duration = FfmpegUtil.getVideoDuration(localPath.toString(), false);
            if (duration == null)
            {
                throw new RuntimeException("无法获取视频时长");
            }

            // 设置视频文件相关信息
            fileUpload.setDuration(duration);
            fileUpload.setFileSize(Files.size(localPath));
            fileUpload.setFilePath(baseKey);

            // 将视频转换为分段TS文件（tsFolder/xxxx.ts 不足4位会补0补到4位）和m3u8（index.m3u8）
            convertVideoToTs(localPath);

            fileUpload.setTransferResult(VideoFileStatus.TRANSCODING_SUCCESS.getStatus());

        }
        catch (Exception e)
        {
            fileUpload.setTransferResult(VideoFileStatus.TRANSCODING_FAIL.getStatus());
            // 删除转码失败的文件
            // 由于源文件在MinIO中，因此本地的删除操作为幂等删除，可以配合3次重试
            // 必须是同步删除，否则可能出现刚产生新文件就被删除的情况
            try
            {
                if (fileUpload.getFilePath() != null && !fileUpload.getFilePath().isEmpty())
                {
                    String dest = Paths.get(Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, fileUpload.getFilePath())
                                       .toString();
                    localFileService.delete(dest);
                }
            }
            catch (InvalidPathException invalidPathException)
            {
                // 文件路径不合法可能是由于 video_info_file_upload 存了脏数据，便于排错
                log.warn("文件路径不合法：{}，异常信息：{}",
                         String.join("/", Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, fileUpload.getFilePath()),
                         invalidPathException.toString());
            }
            catch (Exception innerException)
            {
                // 删除文件失败在日志中记录一下，避免硬盘存脏文件
                log.error("文件删除失败！文件路径：{}，异常信息:{}",
                          Paths.get(Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, fileUpload.getFilePath()),
                          innerException.toString());
            }
            throw new RuntimeException("视频转码失败", e);
        }
        finally
        {
            // 如果转码失败
            if (VideoFileStatus.TRANSCODING_FAIL.getStatus().equals(fileUpload.getTransferResult()))
            {
                // 然后将其归属权设置为未使用，之后有定时任务自动清理
                mediaOwnershipMapper.markAsUnused(fileUpload.getFilePath(), fileUpload.getUserId());

                // 将转码失败文件路径置为空
                fileUpload.setFilePath(null);
            }
            else
            {
                // 如果转码没有失败，将其设置为已使用，虽然之前就已经设置过了，但是这个时候在设置是防止之前转码失败设置为未使用
                mediaOwnershipMapper.markAsUsed(fileUpload.getFilePath(), fileUpload.getUserId(), LocalDateTime.now());
            }

            // 将对VideoInfoFileUpload的修改保存到MySQL
            videoInfoFileUploadMapper.updateByUserIdAndFileId(fileUpload, fileUpload.getUserId(), fileUpload.getFileId());

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
                    // 更新视频信息，计算总时长、标记为待审核
                    Integer totalDuration = videoInfoFileUploadMapper.sumDuration(fileUpload.getVideoId());
                    VideoInfoUpload videoInfoUpload = new VideoInfoUpload();
                    videoInfoUpload.setStatus(VideoStatus.PENDING_REVIEW.getStatus());
                    videoInfoUpload.setDuration(totalDuration);
                    videoInfoUploadMapper.updateByVideoId(videoInfoUpload, fileUpload.getVideoId());

                    Long videoId = fileUpload.getVideoId();
                    // 只查询出有修改的文件，只有这些文件需要上传
                    VideoInfoFileUploadQuery uploadFileQuery = new VideoInfoFileUploadQuery();
                    uploadFileQuery.setVideoId(videoId);
                    uploadFileQuery.setUpdateType(UpdateType.UPDATED.getUpdateType());
                    List <VideoInfoFileUpload> fileList = videoInfoFileUploadMapper.selectList(uploadFileQuery);
                    List <String> sourceBaseKeys = fileList.stream()
                                                           .map(VideoInfoFileUpload::getFilePath)
                                                           .filter(path -> path != null && !path.isBlank())
                                                           .toList();

                    // 必须先删源对象 pending/{baseKey}，再上传 HLS 到 pending/{baseKey}/...
                    // MinIO AIStor 不允许「同名对象」与「以其为前缀的子对象」共存：
                    // 若源文件还在，子对象能 stat 成功但 list 不可见；随后再删源文件会级联清掉刚上传的 HLS
                    deletePendingSourceFiles(sourceBaseKeys);

                    // 把转码文件上传
                    uploadTranscodedFiles(videoId, fileList);
                }
            }

        }
    }

    /**
     * 将该视频下所有分P的转码结果从本地上传到MinIO<hr/>
     * <p>进入此方法时，该视频下所有分P均已转码成功。根据各分P的 baseKey（file_path 字段）定位本机暂存目录，
     * 将目录下的所有文件按相对路径拼接到 {@code pending/<baseKey>/} 下上传</p>
     * <p>单个文件上传失败不会中断其余文件的上传；上传失败按转码失败处理</p>
     *
     * @param videoId        视频ID
     * @param uploadFileList 该视频下所有分P文件记录
     */
    private void uploadTranscodedFiles(Long videoId, List <VideoInfoFileUpload> uploadFileList)
    {
        if (uploadFileList == null || uploadFileList.isEmpty())
        {
            return;
        }

        for (VideoInfoFileUpload uploadFile : uploadFileList)
        {
            String baseKey = uploadFile.getFilePath();
            if (baseKey == null || baseKey.isBlank())
            {
                log.warn("文件路径为空或空白，fileId={}，baseKey={}", uploadFile.getFileId(), baseKey);
                markUploadFailed(videoId, uploadFile);
                continue;
            }

            Path localDir = Paths.get(Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, baseKey);

            try
            {
                fileService.uploadDirectory(localDir, MinioKey.PENDING_PREFIX + baseKey);
                // 上传成功后同步清理本地暂存文件，避免占用磁盘
                localFileService.delete(localDir.toString());
            }
            catch (Exception e)
            {
                log.error("转码文件上传MinIO失败，fileId={}，baseKey={}，异常信息：{}",
                          uploadFile.getFileId(),
                          baseKey,
                          e.toString());

                markUploadFailed(videoId, uploadFile);
            }
        }
    }

    /**
     * 将视频及其对应文件标记为转码失败<hr/>
     * 同时从属表的记录也应该删除
     *
     * @param videoId    视频ID
     * @param uploadFile 上传失败的文件记录
     */
    private void markUploadFailed(Long videoId, VideoInfoFileUpload uploadFile)
    {
        // 先删除从属表记录
        mediaOwnershipMapper.deleteByObjectKey(uploadFile.getFilePath());

        // 将文件上传状态标记为转码失败，清除路径
        uploadFile.setTransferResult(VideoFileStatus.TRANSCODING_FAIL.getStatus());
        uploadFile.setFilePath(null);
        videoInfoFileUploadMapper.updateByUserIdAndFileId(uploadFile, uploadFile.getUserId(), uploadFile.getFileId());

        // 将视频状态标记为转码失败
        VideoInfoUpload videoInfoUpload = new VideoInfoUpload();
        videoInfoUpload.setStatus(VideoStatus.TRANSCODING_FAIL.getStatus());
        videoInfoUploadMapper.updateByVideoId(videoInfoUpload, videoId);
    }

    /**
     * 删除MinIO中该视频下各分P的原始源文件<hr/>
     * <p><b>必须在上传转码产物之前调用。</b>源对象 key 为 {@code pending/{baseKey}}，
     * HLS 落在 {@code pending/{baseKey}/...}；若先传 HLS 再删源，AIStor 会把子对象一并清掉</p>
     *
     * @param baseKeys 各分P的 baseKey 列表
     */
    private void deletePendingSourceFiles(List <String> baseKeys)
    {
        for (String baseKey : baseKeys)
        {
            try
            {
                innerVideoFileFeignClient.deleteObject(MinioKey.PENDING_PREFIX + baseKey);
            }
            catch (Exception e)
            {
                log.error("删除MinIO原始源文件失败，baseKey={}，异常信息：{}", baseKey, e.toString());
            }
        }
    }

    /**
     * 将视频转换为TS分片文件<hr/>
     * 结果临时保存在本地，路径如下：
     * <pre>
     * &lt;tmp&gt;/&lt;key&gt;/
     *      |--- master.m3u8
     *      |--- 720P
     *          |--- index.m3u8
     *          |--- tsFolder
     *              | --- 一群ts分片文件
     *      |--- 480P
     *          |--- index.m3u8
     *          |--- tsFolder
     *              | --- 一群ts分片文件
     * </pre>
     *
     * @param videoPath 原视频路径
     * @throws IOException 由于涉及储存，可能产生IO异常
     */
    private void convertVideoToTs(Path videoPath) throws IOException
    {
        Path parentPath = videoPath.getParent();
        String parentPathStr = parentPath.toString();

        // 获得视频宽高尺寸
        FfmpegUtil.VideoSize sourceSize = FfmpegUtil.getVideoSize(videoPath.toString());
        List <HlsVariant> variants = getHlsVariants(sourceSize);

        for (HlsVariant variant : variants)
        {
            String outputFolderPathStr = Paths.get(parentPathStr, variant.getFolderName()).toString();
            FfmpegUtil.convertVideoToTs(videoPath.toString(),
                                        outputFolderPathStr,
                                        variant.getWidth(),
                                        variant.getHeight(),
                                        variant.getVideoBitrateKbps(),
                                        false);
        }

        writeMasterM3u8(parentPath, variants);

        Files.deleteIfExists(videoPath);
    }

    /**
     * 获取HLS属性
     *
     * @param sourceSize 原始资源尺寸
     * @return HLS属性列表
     */
    private List <HlsVariant> getHlsVariants(FfmpegUtil.VideoSize sourceSize)
    {
        List <HlsVariant> variants = new ArrayList <>();

        // 如果视频高度大于等于720
        if (sourceSize.height() >= 720)
        {
            // 将宽度等比缩小至720p的尺寸
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

        // 如果视频高度大于等于480
        if (sourceSize.height() >= 480)
        {
            // 将宽度等比缩小至480p的尺寸
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
        // 如果高度比480还小
        else
        {
            // 保留原始尺寸
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

    /**
     * 获取偶数尺寸
     *
     * @param value 输入值
     * @return 偶数值
     */
    private int evenSize(int value)
    {
        if (value <= 2) return 2;
        return value % 2 == 0 ? value : value - 1;
    }

    /**
     * 将不同分辨率的TS分片文件的索引文件写入master.m3u8
     *
     * @param folderPath 文件夹路径
     * @param variants   变量
     * @throws IOException 由于涉及储存，可能产生IO异常
     */
    private void writeMasterM3u8(Path folderPath, List <HlsVariant> variants) throws IOException
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
                                   %s/%s
                                   """.formatted(variant.getBandwidth(),
                                                 variant.getAverageBandwidth(),
                                                 variant.getWidth(),
                                                 variant.getHeight(),
                                                 variant.getVideoCodec(),
                                                 variant.getFolderName(),
                                                 Constants.M3U8_NAME));
        }
        // 写入主m3u8文件
        Files.writeString(folderPath.resolve(Constants.MASTER_M3U8_NAME), content.toString(), StandardCharsets.UTF_8);
    }

    @Getter
    @Setter
    @AllArgsConstructor
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
    }

}
