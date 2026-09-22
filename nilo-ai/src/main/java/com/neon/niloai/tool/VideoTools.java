package com.neon.niloai.tool;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.neon.niloai.entity.vo.CitedVideoVO;
import com.neon.niloai.entity.vo.VideoHitVO;
import com.neon.niloai.service.VideoVectorIndexService;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 注册给视频检索模型的工具<hr/>
 *
 * <p>模型只会回一句「我要调哪个、传什么参数」，真正执行的永远是这里的 Java 方法。
 * 两个工具都是只读的。
 *
 * <p>模型靠 {@link Tool#description()} 和 {@link ToolParam#description()} 决定调不调、怎么传参，
 * 描述写得含糊模型就会乱调，改描述时要当心。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class VideoTools
{
    /**
     * toolContext 里存放本次请求检索到的视频，调用结束后由调用方取出填进返回值
     */
    public static final String CTX_CITED_VIDEOS = "citedVideos";

    private static final int SEARCH_SIZE = 5;

    /**
     * 返回给模型的检索原文长度上限，避免简介过长占满上下文
     */
    private static final int SNIPPET_MAX = 400;

    /**
     * 主站的视频索引，时长、播放量这些详情只有这里有
     */
    private static final String VIDEO_INFO_INDEX = "video_info_doc";

    private final VectorStore vectorStore;

    private final ElasticsearchClient elasticsearchClient;

    @Tool(description = """
            在 Nilo 站内按语义检索视频，返回最相关的最多 5 条，每条包含 videoId、标题和检索原文（标题、标签、简介）。
            检索按意思匹配，同义说法也能命中，比如"垃圾回收"能找到讲 GC 的视频。""")
    public List <VideoHitVO> searchVideo(
            @ToolParam(description = "检索词。用你从用户问题里提炼出的主题，不要原样照抄整句话") String query,
            ToolContext toolContext)
    {
        log.info("工具调用 searchVideo, query={}", query);
        if (!StringUtils.hasText(query))
        {
            return List.of();
        }

        List <Document> documents;
        try
        {
            documents = vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(SEARCH_SIZE).build());
        }
        catch (RuntimeException e)
        {
            log.error("工具 searchVideo 检索失败, query={}", query, e);
            throw new IllegalStateException("视频检索暂时不可用");
        }
        if (documents == null)
        {
            return List.of();
        }

        CitedVideoCollector cited = citedVideos(toolContext);
        List <VideoHitVO> hits = new ArrayList <>(documents.size());
        for (Document document : documents)
        {
            Map <String, Object> metadata = document.getMetadata();
            Long videoId = Long.valueOf(String.valueOf(metadata.get(VideoVectorIndexService.META_VIDEO_ID)));
            Object name = metadata.get(VideoVectorIndexService.META_VIDEO_NAME);
            String videoName = name == null ? null : String.valueOf(name);
            hits.add(new VideoHitVO(videoId, videoName, snippet(document.getText())));
            if (cited != null)
            {
                cited.add(new CitedVideoVO(videoId, videoName));
            }
        }
        return hits;
    }

    @Tool(description = """
            按 videoId 查询视频详情，包括时长 duration（单位：秒）、播放量 playCount、弹幕数 danmakuCount、收藏数 collectCount。
            只能传 searchVideo 返回过、或者之前对话里出现过的 videoId。站内查不到时返回 null。""")
    public VideoInfoDoc getVideoDetail(@ToolParam(description = "视频 id，取自 searchVideo 的返回结果或之前的对话") Long videoId,
                                       ToolContext toolContext)
    {
        log.info("工具调用 getVideoDetail, videoId={}", videoId);
        VideoInfoDoc video;
        try
        {
            video = elasticsearchClient.get(request -> request.index(VIDEO_INFO_INDEX).id(String.valueOf(videoId)),
                                            VideoInfoDoc.class).source();
        }
        catch (IOException | RuntimeException e)
        {
            log.error("工具 getVideoDetail 查询失败, videoId={}", videoId, e);
            throw new IllegalStateException("视频详情暂时不可用");
        }
        // 追问「第二个多长」时这一轮不会检索，这里也记一笔，返回的 videos 才不会是空的
        CitedVideoCollector cited = citedVideos(toolContext);
        if (video != null && cited != null)
        {
            cited.add(new CitedVideoVO(videoId, video.getVideoName()));
        }
        return video;
    }

    private CitedVideoCollector citedVideos(ToolContext toolContext)
    {
        if (toolContext == null)
        {
            return null;
        }
        return toolContext.getContext().get(CTX_CITED_VIDEOS) instanceof CitedVideoCollector cited ? cited : null;
    }

    /**
     * 将保存的原始text规整为有最大长度限制的片段
     *
     * @param text 原始text
     * @return 片段
     */
    private String snippet(String text)
    {
        if (!StringUtils.hasText(text))
        {
            return "（无）";
        }
        String trimmed = text.trim().replace('\n', ' ');
        if (trimmed.length() <= SNIPPET_MAX)
        {
            return trimmed;
        }
        return trimmed.substring(0, SNIPPET_MAX) + "…";
    }
}
