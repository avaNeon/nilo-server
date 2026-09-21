package com.neon.niloai.eval;

import com.neon.niloai.service.VideoVectorIndexService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

/**
 * 向量召回评测<hr/>
 *
 * <p>灌入样例 → 逐题检索 → 按题型打印 Hit@5 / Hit@1 / MRR → 删除样例。只出报告，不做断言。
 *
 * <p>需要 Nacos、ES、embedding 服务都可用，在 IDEA 里手动运行；打包时靠 eval 标签跳过。
 */
@Tag("eval")
@SpringBootTest
class RetrievalEvalTest
{
    private static final int TOP_K = 5;

    /**
     * 阿里云 embedding 单次最多 20 条
     */
    private static final int EMBED_BATCH_SIZE = 20;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void evaluate()
    {
        List <Document> videos = EvalFixtureCatalog.videos();
        for (int start = 0 ; start < videos.size() ; start += EMBED_BATCH_SIZE)
        {
            vectorStore.add(videos.subList(start, Math.min(start + EMBED_BATCH_SIZE, videos.size())));
        }
        try
        {
            report("exact", EvalFixtureCatalog.exactCases());
            report("semantic", EvalFixtureCatalog.semanticCases());
        }
        finally
        {
            // 跑完就删，别让假视频留在正式索引里被真实提问搜到
            vectorStore.delete(videos.stream().map(Document::getId).toList());
        }
    }

    /**
     * 跑一组题并打印汇总；没排第一的题逐条列出实际召回，便于看错在哪
     */
    private void report(String type, Map <String, List <Long>> cases)
    {
        int hitsAt5 = 0;
        int hitsAt1 = 0;
        double reciprocalRankSum = 0D;
        StringBuilder misses = new StringBuilder();
        for (Map.Entry <String, List <Long>> evalCase : cases.entrySet())
        {
            List <Document> retrieved = vectorStore.similaritySearch(SearchRequest.builder()
                                                                                  .query(evalCase.getKey())
                                                                                  .topK(TOP_K)
                                                                                  .build());
            int rank = 0;
            for (int i = 0 ; i < retrieved.size() ; i++)
            {
                if (evalCase.getValue().contains(videoId(retrieved.get(i))))
                {
                    rank = i + 1;
                    break;
                }
            }
            if (rank > 0)
            {
                hitsAt5++;
                reciprocalRankSum += 1D / rank;
            }
            if (rank == 1)
            {
                hitsAt1++;
                continue;
            }
            misses.append(String.format("  ✗ %s  期望 %s  %s%n",
                                        evalCase.getKey(),
                                        evalCase.getValue(),
                                        rank == 0 ? "前 5 未命中" : "实际排第 " + rank));
            for (int i = 0 ; i < retrieved.size() ; i++)
            {
                Document document = retrieved.get(i);
                misses.append(String.format("      %d. %d %s%n",
                                            i + 1,
                                            videoId(document),
                                            document.getMetadata().get(VideoVectorIndexService.META_VIDEO_NAME)));
            }
        }
        int total = cases.size();
        System.out.printf("[%s] Hit@5 %d/%d  Hit@1 %d/%d  MRR %.2f%n",
                          type,
                          hitsAt5,
                          total,
                          hitsAt1,
                          total,
                          total == 0 ? 0D : reciprocalRankSum / total);
        System.out.print(misses);
    }

    private Long videoId(Document document)
    {
        return Long.valueOf(String.valueOf(document.getMetadata().get(VideoVectorIndexService.META_VIDEO_ID)));
    }
}
