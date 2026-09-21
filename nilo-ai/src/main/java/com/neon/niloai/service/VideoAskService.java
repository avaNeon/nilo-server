package com.neon.niloai.service;

import com.neon.niloai.entity.enums.IntentType;
import com.neon.niloai.entity.vo.CitedVideoVO;
import com.neon.niloai.entity.vo.VideoAskVO;
import com.neon.niloai.guard.IntentClassifier;
import com.neon.niloai.tool.CitedVideoCollector;
import com.neon.niloai.tool.VideoTools;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class VideoAskService
{
    /**
     * 白名单之外的统一话术
     */
    private static final String REJECT_ANSWER = "我只负责在 Nilo 站内找视频。你可以直接说想看什么，比如「有没有讲多线程的视频」。";

    private final ChatClient videoSearchChatClient;

    private final ChatClient selfIntroChatClient;

    private final IntentClassifier intentClassifier;

    public VideoAskService(@Qualifier("videoSearchChatClient") ChatClient videoSearchChatClient,
                           @Qualifier("selfIntroChatClient") ChatClient selfIntroChatClient,
                           IntentClassifier intentClassifier)
    {
        this.videoSearchChatClient = videoSearchChatClient;
        this.selfIntroChatClient = selfIntroChatClient;
        this.intentClassifier = intentClassifier;
    }

    /**
     * 先过一遍白名单，再按命中的行为分派
     */
    public VideoAskVO ask(String question)
    {
        IntentType intent = intentClassifier.classify(question);
        return switch (intent)
        {
            case VIDEO_SEARCH -> searchAndAnswer(question);
            case SELF_INTRO -> selfIntro(question);
            case REJECT -> reject(question);
        };
    }

    /**
     * 拒绝<hr/>
     * 请求在白名单之外，直接回固定话术并留痕，供后面统计拒绝率、判断白名单要不要扩
     */
    private VideoAskVO reject(String question)
    {
        log.info("请求不在白名单内，已拒绝, question={}", question);
        return new VideoAskVO(REJECT_ANSWER, List.of(), IntentType.REJECT);
    }

    /**
     * 自我介绍<hr/>
     * 让模型介绍自身能力。这个 ChatClient 没有注册任何工具，主题由系统提示词锁死。
     */
    private VideoAskVO selfIntro(String question)
    {
        String answer;
        try
        {
            answer = selfIntroChatClient.prompt().user(question).call().content();
        }
        catch (RuntimeException e)
        {
            log.error("生成自我介绍失败, question={}", question, e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "模型调用失败，请稍后重试");
        }
        if (!StringUtils.hasText(answer))
        {
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "模型没有返回内容，请稍后重试");
        }
        return new VideoAskVO(answer.trim(), List.of(), IntentType.SELF_INTRO);
    }

    /**
     * 视频搜索<hr/>
     * <p>把问题交给带工具的模型，查什么、查几次由模型自己决定。</p>
     * <p>工具执行时会把检索到的视频记进 toolContext，调用结束后只保留回答里提到了 videoId 的那些：
     * 检索结果不一定都相关，是否相关以模型的回答为准；模型若编造了检索里没有的 videoId，也进不了返回值。</p>
     */
    private VideoAskVO searchAndAnswer(String question)
    {
        CitedVideoCollector cited = new CitedVideoCollector();
        String answer;
        try
        {
            answer = videoSearchChatClient.prompt()
                                          .user(question)
                                          .toolContext(Map.of(VideoTools.CTX_CITED_VIDEOS, cited))
                                          .call()
                                          .content();
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
        String trimmedAnswer = answer.trim();
        List <CitedVideoVO> videos = cited.list()
                                          .stream()
                                          .filter(video -> trimmedAnswer.contains(String.valueOf(video.getVideoId())))
                                          .toList();
        return new VideoAskVO(trimmedAnswer, videos, IntentType.VIDEO_SEARCH);
    }
}
