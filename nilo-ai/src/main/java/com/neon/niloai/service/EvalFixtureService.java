package com.neon.niloai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.neon.niloai.entity.vo.EvalFixtureOpVO;
import com.neon.niloai.entity.vo.RetrievalEvalReportVO;
import com.neon.niloai.entity.vo.RetrievalEvalRowVO;
import com.neon.niloai.eval.EvalFixtureCatalog;
import com.neon.niloai.eval.EvalFixtureCatalog.EvalCase;
import com.neon.niloai.eval.EvalFixtureCatalog.EvalVideo;
import com.neon.nilocommon.entity.dto.VideoEmbedSourceDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class EvalFixtureService
{
    static final String META_EVAL_FIXTURE = "evalFixture";

    private static final String KEYWORD_INDEX = "video_info_doc";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int SEARCH_SIZE = 5;

    /**
     * 阿里云 embedding 单次最多 20 条
     */
    private static final int EMBED_BATCH_SIZE = 20;

    private final VectorStore vectorStore;

    private final ElasticsearchClient elasticsearchClient;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:video_ai_vector}")
    private String vectorIndexName;

    /**
     * 写入向量索引和关键词索引，覆盖同一 videoId
     */
    public EvalFixtureOpVO seed()
    {
        List <EvalVideo> videos = EvalFixtureCatalog.videos();
        int vectorCount = addVectors(videos);
        int keywordCount = 0;
        for (EvalVideo video : videos)
        {
            if (indexKeywordDoc(video))
            {
                keywordCount++;
            }
        }
        log.info("评测样例已灌入, vector={}, keyword={}", vectorCount, keywordCount);
        return new EvalFixtureOpVO(vectorCount, keywordCount);
    }

    /**
     * 按固定 videoId 从两个索引删除评测样例
     */
    public EvalFixtureOpVO delete()
    {
        List <String> ids = EvalFixtureCatalog.videoIds().stream().map(String::valueOf).toList();
        try
        {
            vectorStore.delete(ids);
        }
        catch (RuntimeException e)
        {
            log.error("删除向量评测样例失败, index={}", vectorIndexName, e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "删除向量评测样例失败");
        }
        int keywordCount = 0;
        for (String id : ids)
        {
            if (deleteKeywordDoc(id))
            {
                keywordCount++;
            }
        }
        log.info("评测样例已删除, vector={}, keyword={}", ids.size(), keywordCount);
        return new EvalFixtureOpVO(ids.size(), keywordCount);
    }

    /**
     * 只跑当前向量召回的 Hit@5，不调用 Chat
     */
    public RetrievalEvalReportVO evaluateRetrieval()
    {
        List <RetrievalEvalRowVO> rows = new ArrayList <>();
        int hits = 0;
        int hitsAt1 = 0;
        for (EvalCase evalCase : EvalFixtureCatalog.cases())
        {
            List <Long> retrieved = searchVideoIds(evalCase.question());
            Integer rank = firstExpectedRank(evalCase.expectedVideoIds(), retrieved);
            boolean hit = rank != null;
            boolean hitAt1 = rank != null && rank == 1;
            if (hit)
            {
                hits++;
            }
            if (hitAt1)
            {
                hitsAt1++;
            }
            rows.add(new RetrievalEvalRowVO(evalCase.question(),
                                            evalCase.type(),
                                            evalCase.expectedVideoIds(),
                                            retrieved,
                                            hit,
                                            hitAt1,
                                            rank));
        }
        int total = rows.size();
        double hitRate = total == 0 ? 0D : Math.round(hits * 10000D / total) / 10000D;
        double hitAt1Rate = total == 0 ? 0D : Math.round(hitsAt1 * 10000D / total) / 10000D;
        return new RetrievalEvalReportVO(total, hits, hitRate, hitsAt1, hitAt1Rate, rows);
    }

    private int addVectors(List <EvalVideo> videos)
    {
        List <Document> documents = new ArrayList <>(videos.size());
        for (EvalVideo video : videos)
        {
            VideoEmbedSourceDTO source = new VideoEmbedSourceDTO(video.videoId(),
                                                                 video.videoName(),
                                                                 video.tags(),
                                                                 video.introduction());
            String content = VideoVectorIndexService.buildEmbedText(source);
            Map <String, Object> metadata = new HashMap <>();
            metadata.put(VideoVectorIndexService.META_VIDEO_ID, video.videoId());
            metadata.put(VideoVectorIndexService.META_VIDEO_NAME, video.videoName());
            metadata.put(META_EVAL_FIXTURE, true);
            documents.add(new Document(String.valueOf(video.videoId()), content, metadata));
        }
        try
        {
            for (int start = 0 ; start < documents.size() ; start += EMBED_BATCH_SIZE)
            {
                int end = Math.min(start + EMBED_BATCH_SIZE, documents.size());
                vectorStore.add(documents.subList(start, end));
            }
        }
        catch (RestClientResponseException e)
        {
            log.error("评测样例向量化失败, status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "评测样例向量化失败：" + e.getStatusCode().value());
        }
        catch (RuntimeException e)
        {
            log.error("写入向量评测样例失败", e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "写入向量评测样例失败");
        }
        return documents.size();
    }

    private boolean indexKeywordDoc(EvalVideo video)
    {
        Map <String, Object> document = new HashMap <>();
        document.put("videoId", video.videoId());
        document.put("videoName", video.videoName());
        document.put("tags", splitTags(video.tags()));
        document.put("userId", 990000L);
        document.put("duration", 0);
        document.put("categoryId", 0);
        document.put("playCount", 0);
        document.put("danmakuCount", 0);
        document.put("collectCount", 0);
        document.put("lastUpdateTime", LocalDateTime.now().format(DATE_TIME));
        try
        {
            elasticsearchClient.index(request -> request.index(KEYWORD_INDEX)
                                                        .id(String.valueOf(video.videoId()))
                                                        .document(document));
            return true;
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("写入关键词评测样例失败, videoId={}", video.videoId(), e);
            return false;
        }
    }

    private boolean deleteKeywordDoc(String id)
    {
        try
        {
            elasticsearchClient.delete(request -> request.index(KEYWORD_INDEX).id(id));
            return true;
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("删除关键词评测样例失败, videoId={}", id, e);
            return false;
        }
    }

    private List <Long> searchVideoIds(String question)
    {
        List <Document> documents;
        try
        {
            documents = vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(SEARCH_SIZE).build());
        }
        catch (RuntimeException e)
        {
            log.error("评测检索失败, question={}", question, e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "评测检索失败，请稍后重试");
        }
        if (documents == null)
        {
            return List.of();
        }
        List <Long> videoIds = new ArrayList <>();
        for (Document document : documents)
        {
            Long videoId = toLong(document.getMetadata().get(VideoVectorIndexService.META_VIDEO_ID));
            if (videoId == null)
            {
                videoId = toLong(document.getId());
            }
            if (videoId != null)
            {
                videoIds.add(videoId);
            }
        }
        return videoIds;
    }

    private Integer firstExpectedRank(List <Long> expected, List <Long> retrieved)
    {
        for (int i = 0 ; i < retrieved.size() ; i++)
        {
            if (expected.contains(retrieved.get(i)))
            {
                return i + 1;
            }
        }
        return null;
    }

    private List <String> splitTags(String tags)
    {
        if (!StringUtils.hasText(tags))
        {
            return List.of();
        }
        return Arrays.stream(tags.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    private Long toLong(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value)))
        {
            return null;
        }
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
