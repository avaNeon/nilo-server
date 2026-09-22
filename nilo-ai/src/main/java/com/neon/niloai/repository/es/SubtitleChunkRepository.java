package com.neon.niloai.repository.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;

/**
 * 字幕块向量库<hr/>
 * <p>字幕块单独存一个索引，和视频索引 video_ai_vector 分开，互不干扰。</p>
 * <p>这里没有把向量库注册成 Bean，而是自己 new 一个包在里面：Spring AI 自动配置的视频向量库带
 * {@code @ConditionalOnMissingBean}，容器里只要再出现一个 ElasticsearchVectorStore，它就不创建了。</p>
 */
@Slf4j
@Repository
public class SubtitleChunkRepository implements InitializingBean
{
    public static final String INDEX_NAME = "video_subtitle_chunk";

    public static final String META_VIDEO_ID = "videoId";

    public static final String META_VIDEO_NAME = "videoName";

    public static final String META_FILE_ID = "fileId";

    public static final String META_FILE_INDEX = "fileIndex";

    public static final String META_START_SEC = "startSec";

    public static final String META_END_SEC = "endSec";

    /**
     * 这一块里每条字幕带上时间戳，拼成多行，给模型挑准确的时间点用。只存不参与向量化，比块本身多带前 20 秒
     */
    public static final String META_LINES = "lines";

    /**
     * lines 第一行的时间，单位：秒
     */
    public static final String META_LINES_START_SEC = "linesStartSec";

    /**
     * 阿里云 embedding 单次最多 20 条，Spring AI 默认只按 token 数分批，不管条数
     */
    private static final int EMBED_BATCH_SIZE = 20;

    private final ElasticsearchVectorStore vectorStore;

    private final ElasticsearchClient elasticsearchClient;

    public SubtitleChunkRepository(RestClient restClient,
                                   EmbeddingModel embeddingModel,
                                   ElasticsearchClient elasticsearchClient,
                                   @Value("${spring.ai.vectorstore.elasticsearch.dimensions:1024}") int dimensions)
    {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(INDEX_NAME);
        options.setDimensions(dimensions);
        options.setSimilarity(SimilarityFunction.cosine);
        this.vectorStore = ElasticsearchVectorStore.builder(restClient, embeddingModel).options(options).initializeSchema(true).build();
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 索引不存在时按上面的配置建出来
     */
    @Override
    public void afterPropertiesSet()
    {
        vectorStore.afterPropertiesSet();
    }

    public void add(List <Document> documents)
    {
        for (int start = 0 ; start < documents.size() ; start += EMBED_BATCH_SIZE)
        {
            vectorStore.add(documents.subList(start, Math.min(start + EMBED_BATCH_SIZE, documents.size())));
        }
    }

    public void deleteByVideoId(Long videoId)
    {
        vectorStore.delete(new FilterExpressionBuilder().eq(META_VIDEO_ID, videoId).build());
    }

    /**
     * 清空整个索引里的字幕块，全量重建前调用
     */
    public void deleteAll()
    {
        try
        {
            elasticsearchClient.deleteByQuery(request -> request.index(INDEX_NAME).query(query -> query.matchAll(all -> all)));
        }
        catch (IOException e)
        {
            throw new IllegalStateException("清空字幕块索引失败", e);
        }
    }

    /**
     * @param videoId 只搜这个视频；为 null 时搜全部
     */
    public List <Document> search(String query, int topK, Long videoId)
    {
        SearchRequest.Builder request = SearchRequest.builder().query(query).topK(topK);
        if (videoId != null)
        {
            request.filterExpression(new FilterExpressionBuilder().eq(META_VIDEO_ID, videoId).build());
        }
        return vectorStore.similaritySearch(request.build());
    }
}
