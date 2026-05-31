package com.neon.niloadmin.repository.elasticsearch.impl;

import com.neon.niloadmin.repository.elasticsearch.VideoInfoDocExtensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.util.Map;

@RequiredArgsConstructor
public class VideoInfoDocExtensionRepositoryImpl implements VideoInfoDocExtensionRepository
{
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void increasePlayCountByVideoId(long videoId, int increment)
    {
        increaseFieldById(videoId, "playCount", increment);
    }

    @Override
    public void increaseDanmakuCountByVideoId(long videoId, int increment)
    {
        increaseFieldById(videoId, "danmakuCount", increment);
    }

    @Override
    public void decreaseDanmakuCountByVideoId(long videoId, int decrement)
    {
        increaseFieldById(videoId, "danmakuCount", -decrement);
    }

    @Override
    public void increaseCollectCountByVideoId(long videoId, int increment)
    {
        increaseFieldById(videoId, "collectCount", increment);
    }

    @Override
    public void decreaseCollectCountByVideoId(long videoId, int decrement)
    {
        increaseFieldById(videoId, "collectCount", -decrement);
    }

    /**
     * 根据视频ID调整一条 document 的数字字段
     *
     * @param videoId   视频ID
     * @param fieldName 字段名
     * @param increment 增量
     */
    private void increaseFieldById(Long videoId, String fieldName, int increment)
    {
        Map <String, Object> params = Map.of("fieldName", fieldName, "increment", increment);

        String script = """
                long current = ctx._source[params.fieldName] == null ? 0 : ctx._source[params.fieldName];
                long next = current + params.increment;
                ctx._source[params.fieldName] = next < 0 ? 0 : next
                """;

        UpdateQuery updateQuery = UpdateQuery.builder(String.valueOf(videoId))
                                             .withScript(script)
                                             .withScriptType(ScriptType.INLINE)
                                             .withParams(params)
                                             .withLang("painless")
                                             .withRetryOnConflict(3)
                                             .build();

        elasticsearchOperations.update(updateQuery, IndexCoordinates.of("video_info_doc"));
    }
}
