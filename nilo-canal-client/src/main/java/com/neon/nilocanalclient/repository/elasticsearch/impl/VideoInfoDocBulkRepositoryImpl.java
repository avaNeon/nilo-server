package com.neon.nilocanalclient.repository.elasticsearch.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.neon.nilocanalclient.entity.BulkDocOperation;
import com.neon.nilocanalclient.entity.Type;
import com.neon.nilocanalclient.repository.elasticsearch.VideoInfoDocBulkRepository;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class VideoInfoDocBulkRepositoryImpl implements VideoInfoDocBulkRepository
{
    private final ElasticsearchClient elasticsearchClient;

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 使用BulkAPI进行批量操作
     *
     * @param operations 有序操作（同一 videoId 应已折叠为最后一次）
     * @return 失败数量
     */
    @Override
    public int bulkExecute(List <BulkDocOperation> operations)
    {
        if (operations == null || operations.isEmpty())
        {
            return 0;
        }

        // 先找到index名称
        String indexName = elasticsearchOperations.getIndexCoordinatesFor(VideoInfoDoc.class).getIndexName();

        List <BulkOperation> bulkOperations = new ArrayList <>(operations.size());

        for (BulkDocOperation operation : operations)
        {
            if (operation.getType() == Type.INDEX)
            {
                bulkOperations.add(BulkOperation.of(op -> op.index(idx -> idx.index(indexName)
                                                                             .id(String.valueOf(operation.getVideoId()))
                                                                             .document(operation.getDoc()))));
            }
            else if (operation.getType() == Type.DELETE)
            {
                bulkOperations.add(BulkOperation.of(op -> op.delete(del -> del.index(indexName)
                                                                              .id(String.valueOf(operation.getVideoId())))));
            }
        }

        try
        {
            // 使用 BulkAPI 批量操作
            BulkResponse response = elasticsearchClient.bulk(request -> request.operations(bulkOperations));

            // 如果没有错误，返回0
            if (!response.errors())
            {
                return 0;
            }
            // 否则，遍历响应，统计失败数量并返回
            else
            {
                int failCount = 0;
                for (var item : response.items())
                {
                    if (item.error() != null)
                    {
                        failCount++;
                        log.warn("ES bulk item 失败, id={}, op={}, status={}, error={}",
                                 item.id(),
                                 item.operationType(),
                                 item.status(),
                                 item.error().reason());
                    }
                }
                return failCount;
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("ES bulk 请求失败, size=" + bulkOperations.size(), e);
        }
    }
}
