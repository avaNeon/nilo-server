package com.neon.niloai.service;

import com.neon.niloai.feign.web.InnerVideoFeignClient;
import com.neon.nilocommon.entity.dto.VideoEmbedSourceDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoVectorIndexService
{
    private static final int PAGE_SIZE = 20;

    static final String META_VIDEO_ID = "videoId";

    static final String META_VIDEO_NAME = "videoName";

    private final InnerVideoFeignClient innerVideoFeignClient;

    private final VectorStore vectorStore;

    /**
     * 分页读取已发布视频并写入向量索引，同一 videoId 会覆盖旧向量
     */
    public int fullIndex()
    {
        int pageNo = 1;
        int indexed = 0;
        while (true)
        {
            PaginationResponseVO <VideoEmbedSourceDTO> page = fetchPage(pageNo);
            List <VideoEmbedSourceDTO> sourceList = page.getList();
            if (CollectionUtils.isEmpty(sourceList))
            {
                break;
            }
            List <Document> documents = toDocuments(sourceList);
            if (!documents.isEmpty())
            {
                addDocuments(documents, pageNo);
                indexed += documents.size();
            }
            Integer pageTotal = page.getPageTotal();
            if (pageTotal == null || pageNo >= pageTotal)
            {
                break;
            }
            pageNo++;
        }
        log.info("全量向量灌入完成, indexed={}", indexed);
        return indexed;
    }

    private PaginationResponseVO <VideoEmbedSourceDTO> fetchPage(int pageNo)
    {
        ResponseVO <PaginationResponseVO <VideoEmbedSourceDTO>> response;
        try
        {
            response = innerVideoFeignClient.listEmbedSource(pageNo, PAGE_SIZE);
        }
        catch (RuntimeException e)
        {
            log.error("调用 nilo-web 拉取向量化文本失败, pageNo={}", pageNo, e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "拉取视频文本失败，请稍后重试");
        }
        if (response == null || !ResponseVO.STATUS_SUCCESS.equals(response.getStatus()) || response.getData() == null)
        {
            log.warn("拉取向量化文本失败, pageNo={}, status={}, info={}",
                     pageNo,
                     response == null ? null : response.getStatus(),
                     response == null ? null : response.getInfo());
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "拉取视频文本失败，请稍后重试");
        }
        return response.getData();
    }

    private void addDocuments(List <Document> documents, int pageNo)
    {
        try
        {
            vectorStore.add(documents);
        }
        catch (RestClientResponseException e)
        {
            log.error("调用 embedding 失败, status={}, body={}, pageNo={}, size={}",
                      e.getStatusCode(),
                      e.getResponseBodyAsString(),
                      pageNo,
                      documents.size(),
                      e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "向量化失败：" + e.getStatusCode().value());
        }
        catch (RuntimeException e)
        {
            log.error("写入向量索引失败, pageNo={}, size={}", pageNo, documents.size(), e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "写入向量索引失败，请稍后重试");
        }
        log.info("已写入向量索引, pageNo={}, size={}", pageNo, documents.size());
    }

    private List <Document> toDocuments(List <VideoEmbedSourceDTO> sourceList)
    {
        List <Document> documents = new ArrayList <>(sourceList.size());
        for (VideoEmbedSourceDTO source : sourceList)
        {
            if (source == null || source.getVideoId() == null)
            {
                continue;
            }
            String content = buildEmbedText(source);
            if (!StringUtils.hasText(content))
            {
                continue;
            }
            Map <String, Object> metadata = new HashMap <>();
            metadata.put(META_VIDEO_ID, source.getVideoId());
            metadata.put(META_VIDEO_NAME, source.getVideoName());
            documents.add(new Document(String.valueOf(source.getVideoId()), content, metadata));
        }
        return documents;
    }

    /**
     * 标题、标签、简介拼成一条待向量化文本
     */
    static String buildEmbedText(VideoEmbedSourceDTO source)
    {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "标题", source.getVideoName());
        appendLine(builder, "标签", source.getTags());
        appendLine(builder, "简介", source.getIntroduction());
        return builder.toString().trim();
    }

    private static void appendLine(StringBuilder builder, String label, String value)
    {
        if (!StringUtils.hasText(value))
        {
            return;
        }
        if (!builder.isEmpty())
        {
            builder.append('\n');
        }
        builder.append(label).append("：").append(value.trim());
    }
}
