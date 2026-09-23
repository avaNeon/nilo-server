package com.neon.niloai.service;

import com.neon.niloai.entity.enums.IntentType;
import com.neon.niloai.entity.vo.CitedSegmentVO;
import com.neon.niloai.entity.vo.CitedVideoVO;
import com.neon.niloai.entity.vo.TranscriptHitVO;
import com.neon.niloai.entity.vo.VideoAskVO;
import com.neon.niloai.guard.IntentClassifier;
import com.neon.niloai.tool.CitedTranscriptCollector;
import com.neon.niloai.tool.CitedVideoCollector;
import com.neon.niloai.tool.VideoTools;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import com.neon.nilocommon.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class VideoAskService
{
    /**
     * 白名单之外的统一话术
     */
    private static final String REJECT_ANSWER = "我只负责在 Nilo 站内找视频、回答视频里讲到的问题。你可以直接说想看什么，比如「有没有讲多线程的视频」，或者在视频页问我某段内容在第几分钟。";

    private static final String CURRENT_VIDEO_OPEN_TAG = "<当前视频>";

    private static final String CURRENT_VIDEO_CLOSE_TAG = "</当前视频>";

    /**
     * 回答里的片段标记，比如「【P1 5:29】」
     */
    private static final Pattern SEGMENT_MARK = Pattern.compile("【P(\\d+)\\s*((?:\\d+:)?\\d{1,2}:\\d{2})】");

    /**
     * 回答里「《标题》（videoId）」这种写法中的 videoId
     */
    private static final Pattern VIDEO_ID_MARK = Pattern.compile("（(\\d{6,})）");

    /**
     * 回答里的时间点允许超出命中字幕块起止范围的秒数
     */
    private static final int SEGMENT_TOLERANCE_SEC = 5;

    /**
     * 视频检索开始前告诉调用方的进度。拒绝和自我介绍不发进度
     */
    static final String SEARCHING_STATUS = "正在查站内视频和字幕";

    /**
     * 要总结时的固定话术<hr/>
     * 总结在转码时就算好存成文件了，页面上点开就能看。同一个视频被一百个人问，没必要让模型算一百遍
     */
    private static final String SUMMARY_ANSWER = "播放器下面有「AI 总结」，点开就能看到视频的总结，里面还带章节，点一下能跳到对应时间。" + "你也可以直接问我某段内容在第几分钟。";

    private final IntentClassifier intentClassifier;

    private final ChatClient selfIntroChatClient;

    private final ChatClient videoSearchChatClient;

    private final ChatMemory chatMemory;

    private final VideoTools videoTools;

    public VideoAskService(@Qualifier("videoSearchChatClient") ChatClient videoSearchChatClient,
                           @Qualifier("selfIntroChatClient") ChatClient selfIntroChatClient,
                           IntentClassifier intentClassifier,
                           ChatMemory chatMemory,
                           VideoTools videoTools)
    {
        this.videoSearchChatClient = videoSearchChatClient;
        this.selfIntroChatClient = selfIntroChatClient;
        this.intentClassifier = intentClassifier;
        this.chatMemory = chatMemory;
        this.videoTools = videoTools;
    }

    /**
     * 先过一遍白名单，再按命中的行为分派
     *
     * @param conversationId 前端生成的会话 id，同一个 id 就是同一段对话
     * @param videoId        视频页提问时是当前视频，问题默认针对它、字幕只搜它；首页提问时为 null
     */
    public VideoAskVO ask(String question, String conversationId, Long videoId)
    {
        return ask(question, conversationId, videoId, status ->
        {
        });
    }

    /**
     * 和 {@link #ask(String, String, Long)} 相同，检索开始前多回调一次进度
     *
     * @param onStatus 只在要查视频或字幕时调用，参数是给用户看的一句进度
     */
    public VideoAskVO ask(String question, String conversationId, Long videoId, Consumer <String> onStatus)
    {
        String currentVideoName = videoId == null ? null : currentVideoName(videoId);
        IntentType intent = intentClassifier.classify(question, lastReply(conversationId), currentVideoName);
        return switch (intent)
        {
            case VIDEO_SEARCH ->
            {
                onStatus.accept(SEARCHING_STATUS);
                yield searchAndAnswer(question, conversationId, videoId, currentVideoName);
            }
            case VIDEO_SUMMARY -> summaryHint();
            case SELF_INTRO -> selfIntro(question);
            case REJECT -> reject(question);
        };
    }

    /**
     * 这段对话里助手的最后一条回复，给分类器理解追问用；对话第一句时返回 null
     */
    private String lastReply(String conversationId)
    {
        List <Message> history = chatMemory.get(conversationId);
        for (int i = history.size() - 1 ; i >= 0 ; i--)
        {
            if (history.get(i) instanceof AssistantMessage reply)
            {
                return reply.getText();
            }
        }
        return null;
    }

    /**
     * 视频页当前视频的标题，查不到说明 videoId 不对
     */
    private String currentVideoName(Long videoId)
    {
        VideoInfoDoc video;
        try
        {
            video = videoTools.findVideo(videoId);
        }
        catch (RuntimeException e)
        {
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "查询视频失败，请稍后重试");
        }
        if (video == null)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS.getCode(), "视频不存在");
        }
        return video.getVideoName();
    }

    /**
     * 拒绝<hr/>
     * 请求在白名单之外，直接回固定话术并留痕，供后面统计拒绝率、判断白名单要不要扩
     */
    private VideoAskVO reject(String question)
    {
        log.info("请求不在白名单内，已拒绝, question={}", question);
        return new VideoAskVO(REJECT_ANSWER, List.of(), List.of(), IntentType.REJECT);
    }

    /**
     * 要总结整个视频<hr/>
     * 不调模型，直接指向页面上那份转码时就算好的总结
     */
    private VideoAskVO summaryHint()
    {
        return new VideoAskVO(SUMMARY_ANSWER, List.of(), List.of(), IntentType.VIDEO_SUMMARY);
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
        return new VideoAskVO(answer.trim(), List.of(), List.of(), IntentType.SELF_INTRO);
    }

    /**
     * 视频搜索<hr/>
     * <p>把问题交给带工具的模型，查什么、查几次由模型自己决定。</p>
     * <p>工具执行时会把检索到的视频记进 toolContext，调用结束后只保留回答里提到了 videoId 的那些：
     * 检索结果不一定都相关，是否相关以模型的回答为准；模型若编造了检索里没有的 videoId，也进不了返回值。</p>
     * <p>视频页提问时，当前 videoId 放进 toolContext，字幕检索强制只搜这个视频；同时在问题前面注明用户正在看哪个视频。</p>
     *
     * @param scopeVideoId 视频页当前视频，首页提问时为 null
     */
    private VideoAskVO searchAndAnswer(String question, String conversationId, Long scopeVideoId, String currentVideoName)
    {
        CitedVideoCollector cited = new CitedVideoCollector();
        CitedTranscriptCollector transcripts = new CitedTranscriptCollector();
        Map <String, Object> toolContext = new HashMap <>();
        toolContext.put(VideoTools.CTX_CITED_VIDEOS, cited);
        toolContext.put(VideoTools.CTX_CITED_TRANSCRIPTS, transcripts);
        String userText = question;
        if (scopeVideoId != null)
        {
            toolContext.put(VideoTools.CTX_SCOPE_VIDEO_ID, scopeVideoId);
            userText = CURRENT_VIDEO_OPEN_TAG + "《" + sanitize(currentVideoName) + "》（" + scopeVideoId + "）" + CURRENT_VIDEO_CLOSE_TAG + "\n" + question;
        }

        String answer;
        try
        {
            answer = videoSearchChatClient.prompt()
                                          .user(userText)
                                          .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                                          .toolContext(toolContext)
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
        List <CitedSegmentVO> segments = extractSegments(trimmedAnswer, transcripts.list(), scopeVideoId);
        // 模型没检索字幕却写了时间点，或写的时间和命中对不上：这些时间不能留给用户
        final String shownAnswer;
        if (SEGMENT_MARK.matcher(trimmedAnswer).find() && segments.isEmpty())
        {
            log.info("回答里的时间点没有字幕依据，已改为未找到, question={}", question);
            shownAnswer = "字幕里没有查到对应位置。";
        }
        else
        {
            shownAnswer = dropUngroundedMarks(trimmedAnswer, segments);
        }
        List <CitedVideoVO> videos = cited.list()
                                          .stream()
                                          .filter(video -> shownAnswer.contains(String.valueOf(video.getVideoId())))
                                          .toList();
        return new VideoAskVO(shownAnswer, videos, segments, IntentType.VIDEO_SEARCH);
    }


    /**
     * 从回答里找出「【P1 5:29】」这样的片段标记，逐个和字幕命中核对<hr/>
     * <p>标记属于哪个视频：视频页就是当前视频；首页看标记前面最近一次出现的「（videoId）」。</p>
     * <p>时间点必须落在同一视频、同一分P某个命中块的时间范围里，否则丢弃：模型编的时间点跳不过去。</p>
     */
    private List <CitedSegmentVO> extractSegments(String answer, List <TranscriptHitVO> hits, Long scopeVideoId)
    {
        if (hits.isEmpty())
        {
            return List.of();
        }
        Map <String, CitedSegmentVO> segments = new LinkedHashMap <>();
        Matcher matcher = SEGMENT_MARK.matcher(answer);
        while (matcher.find())
        {
            int fileIndex = Integer.parseInt(matcher.group(1));
            int second = parseSeconds(matcher.group(2));
            Long videoId = scopeVideoId != null ? scopeVideoId : lastVideoIdBefore(answer, matcher.start());
            TranscriptHitVO hit = matchHit(hits, videoId, fileIndex, second);
            if (hit == null)
            {
                log.info("回答里的片段对不上字幕命中，已丢弃, mark={}, videoId={}", matcher.group(), videoId);
                continue;
            }
            segments.putIfAbsent(hit.getVideoId() + "-" + fileIndex + "-" + second,
                                 new CitedSegmentVO(hit.getVideoId(), hit.getVideoName(), fileIndex, second));
        }
        return new ArrayList <>(segments.values());
    }

    /**
     * @param videoId 为 null 时不限视频，但对得上的命中必须都来自同一个视频，否则不猜
     */
    private TranscriptHitVO matchHit(List <TranscriptHitVO> hits, Long videoId, int fileIndex, int second)
    {
        TranscriptHitVO found = null;
        for (TranscriptHitVO hit : hits)
        {
            if (hit.getVideoId() == null || hit.getStartSec() == null || hit.getEndSec() == null || !Objects.equals(hit.getFileIndex(),
                                                                                                                    fileIndex))
            {
                continue;
            }
            if (second < hit.getStartSec() - SEGMENT_TOLERANCE_SEC || second > hit.getEndSec() + SEGMENT_TOLERANCE_SEC)
            {
                continue;
            }
            if (videoId != null && !videoId.equals(hit.getVideoId()))
            {
                continue;
            }
            if (found != null && !found.getVideoId().equals(hit.getVideoId()))
            {
                return null;
            }
            found = hit;
        }
        return found;
    }

    private Long lastVideoIdBefore(String answer, int position)
    {
        Long videoId = null;
        Matcher matcher = VIDEO_ID_MARK.matcher(answer);
        while (matcher.find() && matcher.end() <= position)
        {
            videoId = Long.valueOf(matcher.group(1));
        }
        return videoId;
    }

    /**
     * 去掉回答里对不上字幕命中的「【P1 5:29】」。对得上的标记原样留下
     */
    private String dropUngroundedMarks(String answer, List <CitedSegmentVO> segments)
    {
        Matcher matcher = SEGMENT_MARK.matcher(answer);
        StringBuilder kept = new StringBuilder();
        while (matcher.find())
        {
            int fileIndex = Integer.parseInt(matcher.group(1));
            int second = parseSeconds(matcher.group(2));
            boolean grounded = segments.stream()
                                       .anyMatch(segment -> fileIndex == segment.getFileIndex() && second == segment.getStartSec());
            matcher.appendReplacement(kept, grounded ? Matcher.quoteReplacement(matcher.group()) : "");
        }
        matcher.appendTail(kept);
        return kept.toString();
    }

    /**
     * 「5:29」或「1:05:29」转成秒
     */
    private int parseSeconds(String time)
    {
        int seconds = 0;
        for (String part : time.split(":"))
        {
            seconds = seconds * 60 + Integer.parseInt(part);
        }
        return seconds;
    }

    /**
     * 视频标题是用户填的，剔除里面的同名标签，避免提前闭合跳出包裹
     */
    private String sanitize(String text)
    {
        return text == null ? "" : text.replace(CURRENT_VIDEO_OPEN_TAG, "").replace(CURRENT_VIDEO_CLOSE_TAG, "");
    }
}
