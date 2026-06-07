package com.neon.nilomqconsumer.repository.elasticsearch.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.json.JsonData;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import com.neon.nilomqconsumer.repository.elasticsearch.VideoInfoDocExtensionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class VideoInfoDocExtensionRepositoryImpl implements VideoInfoDocExtensionRepository
{
    private final ElasticsearchOperations elasticsearchOperations;

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public int increasePlayCountByVideoId(Map <Long, Integer> playCountMap)
    {
        if (playCountMap == null || playCountMap.isEmpty())
        {
            return 0;
        }

        String script = """
                long current = ctx._source[params.fieldName] == null ? 0 : ctx._source[params.fieldName];
                long next = current + params.increment;
                ctx._source[params.fieldName] = next < 0 ? 0 : next
                """;

        IndexCoordinates indexCoordinates = elasticsearchOperations.getIndexCoordinatesFor(VideoInfoDoc.class);
        String indexName = indexCoordinates.getIndexName();
        List <BulkOperation> operations = new ArrayList <>(playCountMap.size());

        playCountMap.forEach((videoId, increment) ->
                             {
                                 if (videoId == null || increment == null || increment <= 0)
                                 {
                                     return;
                                 }

                                 operations.add(BulkOperation.of(operation -> operation.update(update -> update.index(indexName)
                                                                                                               .id(String.valueOf(videoId))
                                                                                                               .retryOnConflict(3)
                                                                                                               .action(action ->
                                                                                                                               action.script(scriptBuilder -> scriptBuilder
                                                                                                                                       .inline(inline -> inline.lang("painless")
                                                                                                                                               .source(script)
                                                                                                                                               .params("fieldName",JsonData.of("playCount"))
                                                                                                                                               .params("increment",JsonData.of(increment))))))));
                             });

        if (operations.isEmpty())
        {
            return 0;
        }

        try
        {
            BulkResponse response = elasticsearchClient.bulk(request -> request.operations(operations));
            if (!response.errors())
            {
                return 0;
            }

            int failCount = 0;
            for (var item : response.items())
            {
                if (item.error() != null)
                {
                    failCount++;
                    log.warn("ES播放量bulk更新失败，id={}, status={}, error={}", item.id(), item.status(), item.error().reason());
                }
            }
            return failCount;
        }
        catch (IOException e)
        {
            log.warn("ES播放量bulk更新请求失败，batchSize={}", operations.size(), e);
            return operations.size();
        }
    }
}
