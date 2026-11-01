package com.neon.niloai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neon.niloai.entity.vo.VideoFileSummary;
import com.neon.niloai.feign.web.InnerVideoFeignClient;
import com.neon.niloai.tool.VideoTools;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.dto.VideoFileSourceDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 给问答编排服务查视频<hr/>
 * 标题来自主站视频索引；各分P的总结和章节来自 MinIO 里转码时生成的 summary.json，和前端读的是同一份
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoInfoService
{
    /**
     * 每次最多返回5个分P，受上下文限制。
     */
    private static final int PAGE_SIZE = 5;

    private final VideoTools videoTools;

    private final InnerVideoFeignClient innerVideoFeignClient;

    private final MinioClient minioClient;

    private final ObjectMapper objectMapper;

    /**
     * 查视频标题，视频页每轮提问都用它确认 videoId 有效<hr/>
     * 查不到视频时抛业务异常，属于请求参数错误
     */
    public String findVideoName(Long videoId)
    {
        VideoInfoDoc video = videoTools.findVideo(videoId);
        if (video == null)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS.getCode(), "视频不存在");
        }
        return video.getVideoName();
    }

    /**
     * 查各分P的总结和章节，视频页新会话初始化时作为对话背景<hr/>
     * 按 fileIndex 排好序，没有总结的分P不在里面，受上下文限制，最多5个分P
     */
    public List <VideoFileSummary> listSummaries(Long videoId)
    {
        List <VideoFileSummary> files = new ArrayList <>();
        for (VideoFileSourceDTO file : listFiles(videoId))
        {
            VideoFileSummary summary = readSummary(file.getFilePath());
            // 没有总结的分P（没有字幕、老视频没补跑）直接跳过
            if (summary != null)
            {
                summary.setFileIndex(file.getFileIndex());
                files.add(summary);
            }
        }
        files.sort(Comparator.comparing(VideoFileSummary::getFileIndex));
        return files;
    }

    /**
     * 获取部分视频文件
     * 受上下文限制，最多返回5个分P
     *
     * @param videoId 视频ID
     * @return 视频分P文件列表
     */
    private List <VideoFileSourceDTO> listFiles(Long videoId)
    {
        List <VideoFileSourceDTO> files = new ArrayList <>();

        PaginationResponseVO <VideoFileSourceDTO> page = fetchPage(1, videoId);
        if (!CollectionUtils.isEmpty(page.getList()))
        {
            files.addAll(page.getList());
        }

        return files;
    }

    /**
     * 分页获取视频分P文件
     *
     * @param pageNo  页号
     * @param videoId 视频ID
     * @return 分页视频分P文件对象
     */
    private PaginationResponseVO <VideoFileSourceDTO> fetchPage(int pageNo, Long videoId)
    {
        ResponseVO <PaginationResponseVO <VideoFileSourceDTO>> response;
        try
        {
            response = innerVideoFeignClient.listFileSource(pageNo, PAGE_SIZE, videoId);
        }
        catch (RuntimeException e)
        {
            log.error("调用 nilo-web 拉取分P文件失败, pageNo={}, videoId={}", pageNo, videoId, e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "拉取分P文件失败，请稍后重试");
        }
        if (response == null || !ResponseVO.STATUS_SUCCESS.equals(response.getStatus()) || response.getData() == null)
        {
            log.warn("拉取分P文件失败, pageNo={}, videoId={}, info={}",
                     pageNo,
                     videoId,
                     response == null ? null : response.getInfo());
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "拉取分P文件失败，请稍后重试");
        }
        return response.getData();
    }

    /**
     * 从 MinIO 读已发布分P的总结；文件不存在时返回 null
     */
    private VideoFileSummary readSummary(String filePath)
    {
        String key = MinioKey.PUBLIC_PREFIX + filePath + "/" + Constants.SUMMARY_NAME;
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                                                                 .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                 .object(key)
                                                                 .build()))
        {
            return objectMapper.readValue(in, VideoFileSummary.class);
        }
        catch (ErrorResponseException e)
        {
            if (e.errorResponse().code().startsWith("NoSuch"))
            {
                return null;
            }
            else
            {
                throw new IllegalStateException("读取总结失败: " + key, e);
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException("读取总结失败: " + key, e);
        }
    }
}
