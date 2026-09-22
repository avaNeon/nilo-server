package com.neon.niloai.tool;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.neon.niloai.entity.vo.CitedVideoVO;
import com.neon.niloai.entity.vo.TranscriptHitVO;
import com.neon.niloai.entity.vo.VideoHitVO;
import com.neon.niloai.repository.es.SubtitleChunkRepository;
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
 * 几个工具都是只读的。
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

    /**
     * toolContext 里存放本次请求命中的字幕块，调用结束后用来核对回答里的时间点
     */
    public static final String CTX_CITED_TRANSCRIPTS = "citedTranscripts";

    /**
     * toolContext 里存放视频页当前的 videoId。有它时字幕检索强制只搜这个视频，模型改不了
     */
    public static final String CTX_SCOPE_VIDEO_ID = "scopeVideoId";

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

    private final SubtitleChunkRepository subtitleChunkRepository;

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
        VideoInfoDoc video = findVideo(videoId);
        // 追问「第二个多长」时这一轮不会检索，这里也记一笔，返回的 videos 才不会是空的
        CitedVideoCollector cited = citedVideos(toolContext);
        if (video != null && cited != null)
        {
            cited.add(new CitedVideoVO(videoId, video.getVideoName()));
        }
        return video;
    }

    @Tool(description = """
            按语义检索视频字幕，返回最相关的最多 5 个字幕片段。每个片段包含 videoId、标题、第几P（fileIndex）、
            起止秒数，以及这段的字幕原文（一条一行，行首方括号里是这条字幕出现的时间）。
            用户问视频里讲了什么、从哪里开始讲、在第几分钟、在哪一段时必须调用，不能凭记忆或上一轮的时间点回答。
            返回的字幕原文是回答的唯一依据，原文没写的内容不要补充。字幕是视频原声的语言，英文视频就是英文字幕；
            检索词用字幕的语言，回答用户时再用用户的语言转述。""")
    public List <TranscriptHitVO> searchTranscript(
            @ToolParam(description = "检索词。写成字幕的语言，英文视频用英文关键词；不确定视频是什么语言时，中英文关键词写在一起")
            String query,
            @ToolParam(description = "只在这个视频里搜，取自 searchVideo 的返回结果或之前的对话；不传就搜全站。用户在视频页提问时系统会自动限定为当前视频",
                       required = false) Long videoId,
            ToolContext toolContext)
    {
        Long scopeVideoId = scopeVideoId(toolContext);
        Long searchVideoId = scopeVideoId != null ? scopeVideoId : videoId;
        log.info("工具调用 searchTranscript, query={}, videoId={}, scopeVideoId={}", query, videoId, scopeVideoId);
        if (!StringUtils.hasText(query))
        {
            return List.of();
        }

        List <Document> documents;
        try
        {
            documents = subtitleChunkRepository.search(query, SEARCH_SIZE, searchVideoId);
        }
        catch (RuntimeException e)
        {
            log.error("工具 searchTranscript 检索失败, query={}", query, e);
            throw new IllegalStateException("字幕检索暂时不可用");
        }
        if (documents == null)
        {
            return List.of();
        }

        List <TranscriptHitVO> hits = new ArrayList <>(documents.size());
        for (Document document : documents)
        {
            Map <String, Object> metadata = document.getMetadata();
            // 起始时间取 lines 第一行（含前 20 秒前文），和模型看到的字幕范围一致，核对回答里的时间点时才不会误杀
            Object linesStart = metadata.getOrDefault(SubtitleChunkRepository.META_LINES_START_SEC,
                                                      metadata.get(SubtitleChunkRepository.META_START_SEC));
            hits.add(new TranscriptHitVO(toLong(metadata.get(SubtitleChunkRepository.META_VIDEO_ID)),
                                         metadata.get(SubtitleChunkRepository.META_VIDEO_NAME) == null ? null : String.valueOf(
                                                 metadata.get(SubtitleChunkRepository.META_VIDEO_NAME)),
                                         toInteger(metadata.get(SubtitleChunkRepository.META_FILE_INDEX)),
                                         toInteger(linesStart),
                                         toInteger(metadata.get(SubtitleChunkRepository.META_END_SEC)),
                                         String.valueOf(metadata.get(SubtitleChunkRepository.META_LINES))));
        }
        if (toolContext != null && toolContext.getContext().get(CTX_CITED_TRANSCRIPTS) instanceof CitedTranscriptCollector cited)
        {
            cited.addAll(hits);
        }
        return hits;
    }

    /**
     * 按 videoId 查主站视频索引，查不到返回 null。不是工具，给服务端自己用
     */
    public VideoInfoDoc findVideo(Long videoId)
    {
        try
        {
            return elasticsearchClient.get(request -> request.index(VIDEO_INFO_INDEX).id(String.valueOf(videoId)),
                                           VideoInfoDoc.class).source();
        }
        catch (IOException | RuntimeException e)
        {
            log.error("查询视频详情失败, videoId={}", videoId, e);
            throw new IllegalStateException("视频详情暂时不可用");
        }
    }

    private Long scopeVideoId(ToolContext toolContext)
    {
        if (toolContext == null)
        {
            return null;
        }
        return toolContext.getContext().get(CTX_SCOPE_VIDEO_ID) instanceof Long videoId ? videoId : null;
    }

    /**
     * ES 返回的 metadata 里数字可能是 Integer 也可能是 Long，统一转一下
     */
    private static Long toLong(Object value)
    {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private static Integer toInteger(Object value)
    {
        return value == null ? null : Integer.valueOf(String.valueOf(value));
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
