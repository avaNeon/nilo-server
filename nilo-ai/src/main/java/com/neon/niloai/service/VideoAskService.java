package com.neon.niloai.service;

import com.neon.niloai.entity.vo.CitedVideoVO;
import com.neon.niloai.entity.vo.VideoAskVO;
import com.neon.niloai.feign.web.InnerVideoSearchFeignClient;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoInfoDocVO;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoSearchResultVO;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoAskService
{
    /**
     * 查询视频数量
     */
    private static final int SEARCH_SIZE = 5;

    private final InnerVideoSearchFeignClient innerVideoSearchFeignClient;

    private final ChatClient chatClient;

    /**
     * 检索相关视频并基于检索结果生成回答
     */
    public VideoAskVO ask(String question)
    {
        List <VideoInfoDocVO> videos = searchVideos(question);
        String answer;
        try
        {
            answer = chatClient.prompt().user(buildUserMessage(question, videos)).call().content();
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
        return new VideoAskVO(answer.trim(), toCitedVideos(videos));
    }

    /**
     * 用问题当关键词检索视频
     */
    private List <VideoInfoDocVO> searchVideos(String question)
    {
        ResponseVO <VideoSearchResultVO> response;
        try
        {
            response = innerVideoSearchFeignClient.searchVideo(question, SEARCH_SIZE);
        }
        catch (RuntimeException e)
        {
            log.error("调用 nilo-web 搜索失败, question={}", question, e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "检索视频失败，请稍后重试");
        }
        if (response == null || !ResponseVO.STATUS_SUCCESS.equals(response.getStatus()) || response.getData() == null)
        {
            log.warn("关键词搜索失败, question={}, status={}, info={}",
                     question,
                     response == null ? null : response.getStatus(),
                     response == null ? null : response.getInfo());
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "检索视频失败，请稍后重试");
        }
        List <VideoInfoDocVO> videos = response.getData().getVideoInfoDocList();
        return videos == null ? List.of() : videos;
    }

    /**
     * 把问题和检索结果拼成发给模型的用户消息
     */
    private String buildUserMessage(String question, List <VideoInfoDocVO> videos)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：\n").append(question).append("\n\n检索到的视频（最多5条）：\n");
        // 空列表也发给模型，由系统提示词约束其不要编造
        if (CollectionUtils.isEmpty(videos))
        {
            builder.append("（没有检索到任何视频）");
            return builder.toString();
        }
        int index = 1;
        for (VideoInfoDocVO video : videos)
        {
            builder.append(index++)
                   .append(". videoId=")
                   .append(video.getVideoId())
                   .append(", title=")
                   .append(video.getVideoName())
                   .append('\n');
        }
        return builder.toString();
    }

    private List <CitedVideoVO> toCitedVideos(List <VideoInfoDocVO> videos)
    {
        return videos.stream().map(video -> new CitedVideoVO(video.getVideoId(), video.getVideoName())).toList();
    }
}
