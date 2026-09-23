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
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoVectorIndexService
{
    private static final int PAGE_SIZE = 20;

    public static final String META_VIDEO_ID = "videoId";

    public static final String META_VIDEO_NAME = "videoName";

    /**
     * 主文档里简介最多取这么多字符，多出来的切成单独的块
     */
    private static final int INTRO_HEAD_CHARS = 300;

    private static final int INTRO_CHUNK_CHARS = 300;

    /**
     * 简介最长 2000 字符，正常切不满，这里只是兜底
     */
    private static final int MAX_INTRO_CHUNKS = 8;

    private static final Pattern URL = Pattern.compile("https?://\\S+");

    private final InnerVideoFeignClient innerVideoFeignClient;

    private final VectorStore vectorStore;

    /**
     * 分页读取已发布视频并写入向量索引，同一 videoId 会覆盖旧向量
     */
    public int fullIndex()
    {
        return index(null);
    }

    /**
     * 只重建一个视频：发布、改标题简介之后调用
     *
     * @return 写入的文档数
     */
    public int indexVideo(Long videoId)
    {
        int indexed = index(videoId);
        log.info("视频向量重建完成, videoId={}, documents={}", videoId, indexed);
        return indexed;
    }

    /**
     * 删掉一个视频的全部向量：下架、删除之后调用
     */
    public void deleteVideo(Long videoId)
    {
        vectorStore.delete(new FilterExpressionBuilder().eq(META_VIDEO_ID, videoId).build());
        log.info("视频向量已删除, videoId={}", videoId);
    }

    /**
     * @param videoId 只灌这一个视频；为 null 时灌全部
     */
    private int index(Long videoId)
    {
        int pageNo = 1;
        int indexed = 0;
        while (true)
        {
            PaginationResponseVO <VideoEmbedSourceDTO> page = fetchPage(pageNo, videoId);
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

    private PaginationResponseVO <VideoEmbedSourceDTO> fetchPage(int pageNo, Long videoId)
    {
        ResponseVO <PaginationResponseVO <VideoEmbedSourceDTO>> response;
        try
        {
            response = innerVideoFeignClient.listEmbedSource(pageNo, PAGE_SIZE, videoId);
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
            // 简介变短时块数会变少，先按 videoId 删掉旧的，免得留下对不上的残块
            List <Object> videoIds = documents.stream().map(document -> document.getMetadata().get(META_VIDEO_ID)).distinct().toList();
            vectorStore.delete(new FilterExpressionBuilder().in(META_VIDEO_ID, videoIds).build());
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
            if (StringUtils.hasText(content))
            {
                documents.add(document(source, String.valueOf(source.getVideoId()), content));
            }
            // 简介最长 2000 字符，整段压成一个向量重点会被平均掉，超出开头部分的再按段切开单独存
            List <String> rest = restChunks(cleanIntroduction(source.getIntroduction()));
            for (int i = 0 ; i < rest.size() ; i++)
            {
                String chunk = "标题：" + source.getVideoName() + "\n简介（续）：" + rest.get(i);
                documents.add(document(source, source.getVideoId() + "-intro-" + i, chunk));
            }
        }
        return documents;
    }

    private static Document document(VideoEmbedSourceDTO source, String id, String content)
    {
        Map <String, Object> metadata = new HashMap <>();
        metadata.put(META_VIDEO_ID, source.getVideoId());
        metadata.put(META_VIDEO_NAME, source.getVideoName());
        return new Document(id, content, metadata);
    }

    /**
     * 标题、标签、简介开头拼成一条待向量化文本<hr/>
     * 简介只取开头 {@link #INTRO_HEAD_CHARS} 个字符，保证这条文档以标题和标签为主，检索「找视频」时不被长简介稀释
     */
    public static String buildEmbedText(VideoEmbedSourceDTO source)
    {
        String introduction = cleanIntroduction(source.getIntroduction());
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "标题", source.getVideoName());
        appendLine(builder, "标签", source.getTags());
        appendLine(builder, "简介", introduction.length() > INTRO_HEAD_CHARS ? introduction.substring(0, INTRO_HEAD_CHARS) : introduction);
        return builder.toString().trim();
    }

    /**
     * 简介里的链接对检索没有帮助，去掉；数据库里换行存的是字面量 \n，还原成真正的换行
     */
    static String cleanIntroduction(String introduction)
    {
        if (!StringUtils.hasText(introduction))
        {
            return "";
        }
        return URL.matcher(introduction.replace("\\n", "\n")).replaceAll(" ").replaceAll("[ \\t]+", " ").trim();
    }

    /**
     * 简介开头之外的部分，每 {@link #INTRO_CHUNK_CHARS} 个字符一块，尽量切在换行上
     */
    static List <String> restChunks(String introduction)
    {
        List <String> chunks = new ArrayList <>();
        int start = INTRO_HEAD_CHARS;
        while (start < introduction.length() && chunks.size() < MAX_INTRO_CHUNKS)
        {
            int end = Math.min(start + INTRO_CHUNK_CHARS, introduction.length());
            if (end < introduction.length())
            {
                int lineBreak = introduction.lastIndexOf('\n', end);
                if (lineBreak > start)
                {
                    end = lineBreak;
                }
            }
            String chunk = introduction.substring(start, end).trim();
            if (StringUtils.hasText(chunk))
            {
                chunks.add(chunk);
            }
            start = end;
        }
        return chunks;
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
