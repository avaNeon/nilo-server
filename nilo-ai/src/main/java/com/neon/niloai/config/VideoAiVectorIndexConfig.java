package com.neon.niloai.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class VideoAiVectorIndexConfig
{
    @Value("${spring.ai.vectorstore.elasticsearch.index-name:video_ai_vector}")
    private String indexName;

    /**
     * Spring AI 建索引不设副本数，ES 默认 replicas=1；单机需要改成 0
     */
    @Bean
    ApplicationRunner videoAiVectorReplicaZero(ElasticsearchClient elasticsearchClient)
    {
        return args ->
        {
            if (!elasticsearchClient.indices().exists(request -> request.index(indexName)).value())
            {
                log.warn("向量索引不存在，跳过副本设置, index={}", indexName);
                return;
            }
            elasticsearchClient.indices()
                               .putSettings(request -> request.index(indexName)
                                                              .settings(settings -> settings.numberOfReplicas("0")));
            log.info("已将向量索引副本数设为 0, index={}", indexName);
        };
    }
}
