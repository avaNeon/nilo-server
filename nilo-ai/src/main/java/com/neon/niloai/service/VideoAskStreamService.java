package com.neon.niloai.service;

import com.neon.niloai.entity.vo.AskStreamDoneVO;
import com.neon.niloai.entity.vo.VideoAskVO;
import com.neon.nilocommon.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 把一次问答拆成 SSE 推给浏览器<hr/>
 * <p>连接一建立就推进度。正文等 {@link VideoAskService#ask} 核对完时间点再切成小段推，
 * 避免模型生成过程中先把一个对不上字幕的时间显示出来。</p>
 */
@Slf4j
@Service
public class VideoAskStreamService
{
    private static final long TIMEOUT_MS = 90_000;

    /**
     * 一段大约几个字。太碎会把「【P1 3:15】」从中间切开
     */
    private static final int CHUNK_CHARS = 12;

    /**
     * 两段之间停一下，浏览器才看得清是逐段出来的
     */
    private static final long CHUNK_GAP_MS = 25;

    private static final String LOOKING_STATUS = "正在看你的问题";

    private static final String ERROR_TEXT = "出了点问题，请稍后再试。";

    private final VideoAskService videoAskService;

    private final Executor askStreamExecutor;

    public VideoAskStreamService(VideoAskService videoAskService, @Qualifier("askStreamExecutor") Executor askStreamExecutor)
    {
        this.videoAskService = videoAskService;
        this.askStreamExecutor = askStreamExecutor;
    }

    /**
     * 打开一条 SSE。真正的提问在 {@link #askStreamExecutor} 里跑
     */
    public SseEmitter open(String question, String conversationId, Long videoId)
    {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        try
        {
            askStreamExecutor.execute(() -> write(emitter, question, conversationId, videoId));
        }
        catch (RuntimeException e)
        {
            log.warn("问答流式任务没排上队, question={}", question, e);
            sendQuietly(emitter, event("error", Map.of("text", ERROR_TEXT)));
            emitter.complete();
        }
        return emitter;
    }

    private void write(SseEmitter emitter, String question, String conversationId, Long videoId)
    {
        try
        {
            send(emitter, event("status", Map.of("text", LOOKING_STATUS)));
            VideoAskVO result = videoAskService.ask(question,
                                                    conversationId,
                                                    videoId,
                                                    status -> send(emitter, event("status", Map.of("text", status))));
            String answer = result.getAnswer() == null ? "" : result.getAnswer();
            List <String> chunks = chunks(answer);
            for (int i = 0 ; i < chunks.size() ; i++)
            {
                send(emitter, event("delta", Map.of("text", chunks.get(i))));
                if (i < chunks.size() - 1)
                {
                    Thread.sleep(CHUNK_GAP_MS);
                }
            }
            send(emitter, event("done", new AskStreamDoneVO(answer, result.getVideos(), result.getSegments())));
            emitter.complete();
        }
        catch (ClientClosedException e)
        {
            log.info("浏览器关掉了问答连接, question={}", question);
        }
        catch (BusinessException e)
        {
            log.warn("问答失败, question={}", question, e);
            sendQuietly(emitter, event("error", Map.of("text", e.getMessage() == null ? ERROR_TEXT : e.getMessage())));
            completeQuietly(emitter);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            completeQuietly(emitter);
        }
        catch (RuntimeException e)
        {
            log.error("问答流式失败, question={}", question, e);
            sendQuietly(emitter, event("error", Map.of("text", ERROR_TEXT)));
            completeQuietly(emitter);
        }
    }

    /**
     * 按字数切开。碰到「【P1 3:15】」这种片段标记时，整段标记留在同一块里
     */
    static List <String> chunks(String text)
    {
        List <String> chunks = new ArrayList <>();
        int index = 0;
        while (index < text.length())
        {
            int end = Math.min(text.length(), index + CHUNK_CHARS);
            int mark = text.indexOf('【', index);
            if (mark >= index && mark < end)
            {
                int close = text.indexOf('】', mark);
                if (close >= 0)
                {
                    end = close + 1;
                }
            }
            chunks.add(text.substring(index, end));
            index = end;
        }
        return chunks;
    }

    private static SseEmitter.SseEventBuilder event(String name, Object data)
    {
        return SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON);
    }

    private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event)
    {
        // 工作线程可能比 Spring 把这条连接交给 SseEmitter 更早跑起来，那时 send 会说 handler 还没初始化
        for (int waited = 0 ; ; waited++)
        {
            try
            {
                emitter.send(event);
                return;
            }
            catch (IllegalStateException e)
            {
                if (waited >= 20 || e.getMessage() == null || !e.getMessage().contains("not been initialized"))
                {
                    throw new ClientClosedException(new IOException(e));
                }
                try
                {
                    Thread.sleep(10);
                }
                catch (InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                    throw new ClientClosedException(new IOException(interrupted));
                }
            }
            catch (IOException e)
            {
                throw new ClientClosedException(e);
            }
        }
    }

    private static void sendQuietly(SseEmitter emitter, SseEmitter.SseEventBuilder event)
    {
        try
        {
            emitter.send(event);
        }
        catch (IOException | RuntimeException ignored)
        {
            // 连接已经断了，错误事件送不出去
        }
    }

    private static void completeQuietly(SseEmitter emitter)
    {
        try
        {
            emitter.complete();
        }
        catch (RuntimeException ignored)
        {
            // 超时回调可能已经关过
        }
    }

    /**
     * 浏览器断开。用来从提问流程里跳出来，不再继续推
     */
    private static final class ClientClosedException extends RuntimeException
    {
        private ClientClosedException(IOException cause)
        {
            super(cause);
        }
    }
}
