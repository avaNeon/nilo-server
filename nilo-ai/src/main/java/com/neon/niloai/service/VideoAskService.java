package com.neon.niloai.service;

import com.neon.niloai.entity.vo.CitedVideoVO;
import com.neon.niloai.entity.vo.VideoAskVO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoAskService
{
    /**
     * 查询视频数量
     */
    private static final int SEARCH_SIZE = 5;

    /**
     * 发给模型的检索原文长度上限，避免简介过长占满上下文
     */
    private static final int SNIPPET_MAX = 400;

    private final VectorStore vectorStore;

    private final ChatClient chatClient;

    /**
     * 检索相关视频并基于检索结果生成回答
     */
    public VideoAskVO ask(String question)
    {
        List <Document> documents = searchVideos(question);
        String answer;
        try
        {
            answer = chatClient.prompt().user(buildUserMessage(question, documents)).call().content();
        }
        catch (RestClientResponseException e)
        {
            log.error("调用模型失败, status={}, body={}, question={}",
                      e.getStatusCode(),
                      e.getResponseBodyAsString(),
                      question,
                      e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "模型调用失败：" + e.getStatusCode().value());
        }
        catch (RuntimeException e)
        {
            log.error("调用模型失败, question={}", question, e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "模型调用失败，请稍后重试");
        }
        if (!StringUtils.hasText(answer))
        {
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "模型没有返回内容，请稍后重试");
        }
        return new VideoAskVO(answer.trim(), toCitedVideos(documents));
    }

    /**
     * 用问题向量在 video_ai_vector 里取最相近的 5 条
     */
    private List <Document> searchVideos(String question)
    {
        try
        {
            List <Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                                                                                  .query(question)
                                                                                  .topK(SEARCH_SIZE)
                                                                                  .build());
            return documents == null ? List.of() : documents;
        }
        catch (RuntimeException e)
        {
            log.error("向量检索失败, question={}", question, e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "检索视频失败，请稍后重试");
        }
    }

    /**
     * 把问题和检索原文（标题/标签/简介）拼成发给模型的用户消息
     */
    private String buildUserMessage(String question, List <Document> documents)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：\n").append(question).append("\n\n检索到的视频（最多5条）：\n");
        if (CollectionUtils.isEmpty(documents))
        {
            builder.append("（没有检索到任何视频）");
            return builder.toString();
        }
        int index = 1;
        for (Document document : documents)
        {
            CitedVideoVO video = toCitedVideo(document);
            if (video == null)
            {
                continue;
            }
            builder.append(index++)
                   .append(". videoId=")
                   .append(video.getVideoId())
                   .append(", title=")
                   .append(video.getVideoName())
                   .append('\n')
                   .append("   检索原文：")
                   .append(snippet(document.getText()))
                   .append('\n');
        }
        if (index == 1)
        {
            builder.append("（没有检索到任何视频）");
        }
        return builder.toString();
    }

    private List <CitedVideoVO> toCitedVideos(List <Document> documents)
    {
        List <CitedVideoVO> videos = new ArrayList <>(documents.size());
        for (Document document : documents)
        {
            CitedVideoVO video = toCitedVideo(document);
            if (video != null)
            {
                videos.add(video);
            }
        }
        return videos;
    }

    private CitedVideoVO toCitedVideo(Document document)
    {
        Map <String, Object> metadata = document.getMetadata();
        Long videoId = toLong(metadata.get(VideoVectorIndexService.META_VIDEO_ID));
        if (videoId == null)
        {
            videoId = toLong(document.getId());
        }
        if (videoId == null)
        {
            return null;
        }
        Object name = metadata.get(VideoVectorIndexService.META_VIDEO_NAME);
        return new CitedVideoVO(videoId, name == null ? null : String.valueOf(name));
    }

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

    private Long toLong(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value)))
        {
            return null;
        }
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
