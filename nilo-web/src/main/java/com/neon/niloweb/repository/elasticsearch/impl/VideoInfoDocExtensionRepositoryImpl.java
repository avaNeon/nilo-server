package com.neon.niloweb.repository.elasticsearch.impl;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoInfoDocListWithPagination;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.niloweb.repository.elasticsearch.VideoInfoDocExtensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class VideoInfoDocExtensionRepositoryImpl implements VideoInfoDocExtensionRepository
{
    private final ElasticsearchOperations elasticsearchOperations;

    private final String HIGHLIGHT_FIELD = "videoName";
    private final String highlightPreTag = "<span class=\"highlight\">";
    private final String highlightPostTag = "</span>";


    @Override
    public VideoInfoDocListWithPagination searchVideoInfo(String keyword,
                                                          Integer pageNo,
                                                          Integer pageSize,
                                                          Boolean useHighlight,
                                                          String sortFieldName,
                                                          Boolean useScoreSort)
    {
        // 获取查询结果
        SearchHits <VideoInfoDoc> searchHits = searchHighlightVideoResult(keyword,
                                                                          pageNo,
                                                                          pageSize,
                                                                          useHighlight,
                                                                          sortFieldName,
                                                                          useScoreSort);

        // 如果查询结果为 null ，返回空数组
        if (searchHits == null)
        {
            return new VideoInfoDocListWithPagination(new PageCalculator(pageNo, 0, pageSize), new ArrayList <>());
        }

        // 取出查询结果
        List <VideoInfoDoc> videoInfoDocList = searchHits.getSearchHits().stream().map(hit ->
                                                                                       {
                                                                                           // 获取搜索结果
                                                                                           VideoInfoDoc doc = hit.getContent();

                                                                                           if (Boolean.TRUE.equals(useHighlight))
                                                                                           {
                                                                                               // 获取高亮记录
                                                                                               List <String> highlightVideoName = hit.getHighlightField(
                                                                                                       HIGHLIGHT_FIELD);

                                                                                               // 如果存在高亮记录
                                                                                               if (!highlightVideoName.isEmpty())
                                                                                               {
                                                                                                   doc.setVideoName(
                                                                                                           highlightVideoName.get(
                                                                                                                   0));
                                                                                               }
                                                                                           }

                                                                                           // 拆包
                                                                                           return doc;
                                                                                       }).toList();
        // 取出总记录条数（我们后续把long转化成int，应该没啥太大问题）
        long totalCount = searchHits.getTotalHits();

        return new VideoInfoDocListWithPagination(new PageCalculator(pageNo, (int) totalCount, pageSize), videoInfoDocList);
    }

    /**
     * <b>从ES中搜索记录，并按照指定方式排序</b><hr/>
     * 默认高亮字段名称为"videoName"
     *
     * @param keyword       搜索的关键词 （在videoName和tags中搜索，<b>videoName有2倍权重</b>）
     * @param pageNo        页号
     * @param pageSize      页大小
     * @param useHighlight  是否开始高亮
     * @param sortFieldName 排序的字段名（递减排序）
     * @param useScoreSort  是否追加相关性分数排序
     * @return <b>包装</b>的搜索结果
     */
    private SearchHits <VideoInfoDoc> searchHighlightVideoResult(String keyword,
                                                                 Integer pageNo,
                                                                 Integer pageSize,
                                                                 Boolean useHighlight,
                                                                 String sortFieldName,
                                                                 Boolean useScoreSort)
    {
        // 如果没关键词，返回 null
        if (!StringUtils.hasText(keyword))
        {
            return null;
        }

        // 转换一下页号
        int pageIndex = Math.max(pageNo - 1, 0);

        // 构建查询体
        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                                                     .withQuery(q -> q.multiMatch(m -> m.query(keyword)
                                                                                        .fields(HIGHLIGHT_FIELD + "^2",
                                                                                                "tags"))) // 关键词匹配视频名称、tags
                                                     .withSort(s -> s.field(field -> field.field(sortFieldName)
                                                                                          .order(SortOrder.Desc))) // 根据我们指定的字段递减排序
                                                     .withPageable(PageRequest.of(pageIndex, pageSize)); // 分页

        if (Boolean.TRUE.equals(useScoreSort))
        {
            queryBuilder.withSort(s -> s.score(score -> score.order(SortOrder.Desc))); // 综合排序时用相关性分数兜底
        }

        // 如果需要高亮，装配上高亮查询对象
        if (Boolean.TRUE.equals(useHighlight))
        {
            queryBuilder.withHighlightQuery(buildVideoNameHighlightQuery());
        }

        // 开始查询！
        return elasticsearchOperations.search(queryBuilder.build(), VideoInfoDoc.class);
    }

    /**
     * <b>构建高亮查询对象</b>
     *
     * @return HighlightQuery
     */
    private HighlightQuery buildVideoNameHighlightQuery()
    {
        // 构建高亮参数
        // numberOfFragments=0：返回整段字段并高亮，而不是只返回匹配片段（否则长标题会被截成 "feat.xxx"）
        HighlightParameters highlightParameters = HighlightParameters.builder()
                                                                     .withPreTags(highlightPreTag)
                                                                     .withPostTags(highlightPostTag)
                                                                     .withNumberOfFragments(0)
                                                                     .withRequireFieldMatch(true) // 只给 videoName 做高亮
                                                                     .build();

        Highlight highlight = new Highlight(highlightParameters, List.of(new HighlightField(HIGHLIGHT_FIELD)));

        return new HighlightQuery(highlight, VideoInfoDoc.class); // 高亮，并把PO交给ES做属性名映射
    }
}
