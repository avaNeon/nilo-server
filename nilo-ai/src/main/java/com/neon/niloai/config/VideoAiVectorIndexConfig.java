package com.neon.niloai.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neon.niloai.repository.es.SubtitleChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class VideoAiVectorIndexConfig
{
    @Value("${spring.ai.vectorstore.elasticsearch.index-name:video_ai_vector}")
    private String indexName;

    /**
     * ES 客户端改用项目的 ObjectMapper<hr/>
     * Spring Boot 默认给 ES 客户端 new 一个裸的 ObjectMapper，读不了 VideoInfoDoc 的 lastUpdateTime，也不认 _class 字段。
     * 换成全局配置过的这个就能直接复用 VideoInfoDoc。Spring AI 的向量库自己 new ObjectMapper，不受影响。
     */
    @Bean
    JsonpMapper jsonpMapper(ObjectMapper objectMapper)
    {
        return new JacksonJsonpMapper(objectMapper);
    }

    /**
     * Spring AI 建索引不设副本数，ES 默认 replicas=1；单机需要改成 0。视频索引和字幕块索引都要改
     */
    @Bean
    ApplicationRunner videoAiVectorReplicaZero(ElasticsearchClient elasticsearchClient)
    {
        return args ->
        {
            for (String index : List.of(indexName, SubtitleChunkRepository.INDEX_NAME))
            {
                if (!elasticsearchClient.indices().exists(request -> request.index(index)).value())
                {
                    log.warn("向量索引不存在，跳过副本设置, index={}", index);
                    continue;
                }
                elasticsearchClient.indices()
                                   .putSettings(request -> request.index(index).settings(settings -> settings.numberOfReplicas("0")));
                log.info("已将向量索引副本数设为 0, index={}", index);
            }
        };
    }
}
