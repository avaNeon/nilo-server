package com.neon.niloai.service;

import com.neon.niloai.entity.vo.*;
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

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class EvalFixtureService
{
    static final String META_EVAL_FIXTURE = "evalFixture";

    private static final int SEARCH_SIZE = 5;

    /**
     * 阿里云 embedding 单次最多 20 条
     */
    private static final int EMBED_BATCH_SIZE = 20;

    private final VectorStore vectorStore;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:video_ai_vector}")
    private String vectorIndexName;

    /**
     * 写入向量索引，覆盖同一 videoId
     */
    public EvalFixtureOpVO seed()
    {
        int vectorCount = addVectors(EvalFixtureCatalog.videos());
        log.info("评测样例已灌入, vector={}", vectorCount);
        return new EvalFixtureOpVO(vectorCount);
    }

    /**
     * 按固定 videoId 从向量索引删除评测样例
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
        log.info("评测样例已删除, vector={}", ids.size());
        return new EvalFixtureOpVO(ids.size());
    }

    /**
     * 只跑当前向量召回的 Hit@5，不调用 Chat
     */
    public RetrievalEvalReportVO evaluateRetrieval()
    {
        List <RetrievalEvalRowVO> rows = new ArrayList <>();
        for (EvalCase evalCase : EvalFixtureCatalog.cases())
        {
            List <RetrievedDocVO> retrieved = searchDocs(evalCase.question());
            Integer rank = firstExpectedRank(evalCase.expectedVideoIds(), retrieved);
            rows.add(new RetrievalEvalRowVO(evalCase.question(),
                                            evalCase.type(),
                                            evalCase.expectedVideoIds(),
                                            retrieved,
                                            rank != null,
                                            rank != null && rank == 1,
                                            rank));
        }
        RetrievalEvalGroupVO overall = summarize(null, rows);
        List <RetrievalEvalGroupVO> groups = new ArrayList <>();
        for (String type : rows.stream().map(RetrievalEvalRowVO::getType).distinct().sorted().toList())
        {
            groups.add(summarize(type, rows.stream().filter(row -> type.equals(row.getType())).toList()));
        }
        return new RetrievalEvalReportVO(overall.getTotal(),
                                         overall.getHits(),
                                         overall.getHitRate(),
                                         overall.getHitsAt1(),
                                         overall.getHitAt1Rate(),
                                         overall.getMrr(),
                                         groups,
                                         rows);
    }

    /**
     * 汇总一组题的 Hit@5、Hit@1 和 MRR；type 传 null 表示整体口径
     */
    private RetrievalEvalGroupVO summarize(String type, List <RetrievalEvalRowVO> rows)
    {
        int total = rows.size();
        int hits = 0;
        int hitsAt1 = 0;
        double reciprocalRankSum = 0D;
        for (RetrievalEvalRowVO row : rows)
        {
            Integer rank = row.getRank();
            if (rank == null)
            {
                continue;
            }
            hits++;
            if (rank == 1)
            {
                hitsAt1++;
            }
            reciprocalRankSum += 1D / rank;
        }
        return new RetrievalEvalGroupVO(type,
                                        total,
                                        hits,
                                        rate(hits, total),
                                        hitsAt1,
                                        rate(hitsAt1, total),
                                        total == 0 ? 0D : round(reciprocalRankSum / total));
    }

    private double rate(int count, int total)
    {
        return total == 0 ? 0D : round(count * 1D / total);
    }

    private double round(double value)
    {
        return Math.round(value * 10000D) / 10000D;
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

    /**
     * 召回前 5 条，连同向量化之前的原始文本一起返回，便于人工看为什么会被召回
     */
    private List <RetrievedDocVO> searchDocs(String question)
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
        List <RetrievedDocVO> docs = new ArrayList <>();
        for (Document document : documents)
        {
            Long videoId = toLong(document.getMetadata().get(VideoVectorIndexService.META_VIDEO_ID));
            if (videoId == null)
            {
                videoId = toLong(document.getId());
            }
            if (videoId != null)
            {
                docs.add(new RetrievedDocVO(videoId, document.getText()));
            }
        }
        return docs;
    }

    private Integer firstExpectedRank(List <Long> expected, List <RetrievedDocVO> retrieved)
    {
        for (int i = 0 ; i < retrieved.size() ; i++)
        {
            if (expected.contains(retrieved.get(i).getVideoId()))
            {
                return i + 1;
            }
        }
        return null;
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
