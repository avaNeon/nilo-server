package com.neon.niloweb;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
@Disabled("Requires a live Elasticsearch cluster and monitor privileges.")
class EsConnectionTest
{
    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    void testConnection() throws IOException
    {
        // 获取集群信息
        InfoResponse info = elasticsearchClient.info();
        System.out.println("ES版本号: " + info.version().number());
        System.out.println("集群名称: " + info.clusterName());
    }
}
