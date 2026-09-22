package com.neon.niloai.service;

import com.neon.niloai.feign.web.InnerVideoFeignClient;
import com.neon.niloai.repository.es.SubtitleChunkRepository;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.dto.VideoFileSourceDTO;
import com.neon.nilocommon.entity.dto.subtitle.SubtitleCueDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字幕切块灌入<hr/>
 * <p>读已发布视频每个分P的字幕 subtitle.srt（原文语言），按 30 秒一块切开写进字幕块向量库。</p>
 * <p>相邻两块重叠 10 秒，避免一句话正好被切在两块中间、哪块都搜不全。只在字幕条目的边界上切，不会把一条字幕切成两半。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SubtitleChunkService
{
    private static final int PAGE_SIZE = 20;

    /**
     * 一块覆盖 30 秒，相邻块重叠 10 秒。评测对比过 30/20、20/10、15/5，块越碎命中率越低
     */
    private static final int WINDOW_MS = 30_000;

    private static final int OVERLAP_MS = 10_000;

    /**
     * 给模型看的字幕往前多带 20 秒（不参与向量化）。话题交界处的块一半还在讲上一个话题，向量排不上来，
     * 命中的往往是话题中段；带上前文，模型才能看到这个话题是从哪一句开始的
     */
    private static final int LEAD_IN_MS = 20_000;

    private static final Pattern SRT_TIME = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})");

    private final InnerVideoFeignClient innerVideoFeignClient;

    private final MinioClient minioClient;

    private final SubtitleChunkRepository subtitleChunkRepository;

    /**
     * 全量重建：先清空字幕块索引，再逐页拉分P、读字幕、切块写入
     *
     * @return 写入的块数
     */
    public int fullIndex()
    {
        subtitleChunkRepository.deleteAll();
        int indexed = indexPages(null);
        log.info("字幕块全量灌入完成, chunks={}", indexed);
        return indexed;
    }

    /**
     * 重建单个视频：先删掉这个视频的旧块再写。分P可能增删、换顺序，所以按视频整体重建
     *
     * @return 写入的块数
     */
    public int indexVideo(Long videoId)
    {
        subtitleChunkRepository.deleteByVideoId(videoId);
        int indexed = indexPages(videoId);
        log.info("字幕块重建完成, videoId={}, chunks={}", videoId, indexed);
        return indexed;
    }

    /**
     * 把给定的分P切块写入，不删旧块。没有字幕的分P（没人声、被判成乱码、老视频没补跑）直接跳过
     *
     * @return 写入的块数
     */
    public int indexFiles(List <VideoFileSourceDTO> files)
    {
        int indexed = 0;
        for (VideoFileSourceDTO file : files)
        {
            List <SubtitleCueDTO> cues = readCues(file.getFilePath());
            if (cues.isEmpty())
            {
                log.info("分P没有字幕，跳过, videoId={}, fileIndex={}", file.getVideoId(), file.getFileIndex());
                continue;
            }
            List <Document> chunks = toChunks(file, cues);
            subtitleChunkRepository.add(chunks);
            indexed += chunks.size();
            log.info("分P字幕已切块写入, videoId={}, fileIndex={}, cues={}, chunks={}",
                     file.getVideoId(),
                     file.getFileIndex(),
                     cues.size(),
                     chunks.size());
        }
        return indexed;
    }

    private int indexPages(Long videoId)
    {
        int pageNo = 1;
        int indexed = 0;
        while (true)
        {
            PaginationResponseVO <VideoFileSourceDTO> page = fetchPage(pageNo, videoId);
            List <VideoFileSourceDTO> files = page.getList();
            if (CollectionUtils.isEmpty(files))
            {
                break;
            }
            indexed += indexFiles(files);
            // 页码越界时 nilo-web 返回的还是最后一页，只能按总页数停
            Integer pageTotal = page.getPageTotal();
            if (pageTotal == null || pageNo >= pageTotal)
            {
                break;
            }
            pageNo++;
        }
        return indexed;
    }

    private PaginationResponseVO <VideoFileSourceDTO> fetchPage(int pageNo, Long videoId)
    {
        ResponseVO <PaginationResponseVO <VideoFileSourceDTO>> response;
        try
        {
            response = innerVideoFeignClient.listFileSource(pageNo, PAGE_SIZE, videoId);
        }
        catch (RuntimeException e)
        {
            log.error("调用 nilo-web 拉取分P文件失败, pageNo={}, videoId={}", pageNo, videoId, e);
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "拉取分P文件失败，请稍后重试");
        }
        if (response == null || !ResponseVO.STATUS_SUCCESS.equals(response.getStatus()) || response.getData() == null)
        {
            log.warn("拉取分P文件失败, pageNo={}, videoId={}, info={}",
                     pageNo,
                     videoId,
                     response == null ? null : response.getInfo());
            throw new BusinessException(ResponseCode.SERVER_ERROR.getCode(), "拉取分P文件失败，请稍后重试");
        }
        return response.getData();
    }

    /**
     * 从 MinIO 读已发布分P的字幕；文件不存在时返回空列表
     */
    private List <SubtitleCueDTO> readCues(String filePath)
    {
        String key = MinioKey.PUBLIC_PREFIX + filePath + "/" + Constants.SUBTITLE_NAME;
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                                                                 .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                 .object(key)
                                                                 .build()))
        {
            return parseSrt(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        catch (ErrorResponseException e)
        {
            if (e.errorResponse().code().startsWith("NoSuch"))
            {
                return List.of();
            }
            throw new IllegalStateException("读取字幕失败: " + key, e);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("读取字幕失败: " + key, e);
        }
    }

    /**
     * 解析 SRT：空行分隔的一块块，每块是序号、时间行、一到多行文字
     */
    static List <SubtitleCueDTO> parseSrt(String content)
    {
        List <SubtitleCueDTO> cues = new ArrayList <>();
        String normalized = content.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n');
        for (String block : normalized.split("\n\\s*\n"))
        {
            String[] lines = block.trim().split("\n");
            for (int i = 0 ; i < lines.length ; i++)
            {
                Matcher matcher = SRT_TIME.matcher(lines[i]);
                if (!matcher.find())
                {
                    continue;
                }
                String text = String.join(" ", List.of(lines).subList(i + 1, lines.length)).trim();
                if (StringUtils.hasText(text))
                {
                    cues.add(new SubtitleCueDTO(toMs(matcher, 1), toMs(matcher, 5), text));
                }
                break;
            }
        }
        return cues;
    }

    /**
     * 按 30 秒一块切，下一块从上一块开始 20 秒后的第一条字幕起，所以相邻块重叠约 10 秒
     */
    static List <Document> toChunks(VideoFileSourceDTO file, List <SubtitleCueDTO> cues)
    {
        List <Document> chunks = new ArrayList <>();
        int start = 0;
        while (start < cues.size())
        {
            long windowStart = cues.get(start).getStartMs();
            int end = start;
            while (end < cues.size() && cues.get(end).getStartMs() < windowStart + WINDOW_MS)
            {
                end++;
            }
            int leadIn = start;
            while (leadIn > 0 && cues.get(leadIn - 1).getStartMs() >= windowStart - LEAD_IN_MS)
            {
                leadIn--;
            }
            chunks.add(toDocument(file, cues.subList(start, end), cues.subList(leadIn, start)));
            if (end >= cues.size())
            {
                break;
            }
            int next = start + 1;
            while (next < end && cues.get(next).getStartMs() < windowStart + WINDOW_MS - OVERLAP_MS)
            {
                next++;
            }
            start = next;
        }
        return chunks;
    }

    /**
     * @param cues       这一块的字幕，拼起来做向量化
     * @param leadInCues 这一块前面 20 秒的字幕，只放进给模型看的 lines
     */
    private static Document toDocument(VideoFileSourceDTO file, List <SubtitleCueDTO> cues, List <SubtitleCueDTO> leadInCues)
    {
        StringBuilder text = new StringBuilder();
        StringBuilder lines = new StringBuilder();
        long endMs = 0;
        for (SubtitleCueDTO cue : leadInCues)
        {
            appendLine(lines, cue);
        }
        for (SubtitleCueDTO cue : cues)
        {
            if (!text.isEmpty())
            {
                text.append(' ');
            }
            text.append(cue.getText());
            appendLine(lines, cue);
            endMs = Math.max(endMs, cue.getEndMs());
        }
        long startMs = cues.get(0).getStartMs();
        long linesStartMs = leadInCues.isEmpty() ? startMs : leadInCues.get(0).getStartMs();
        Map <String, Object> metadata = new HashMap <>();
        metadata.put(SubtitleChunkRepository.META_VIDEO_ID, file.getVideoId());
        metadata.put(SubtitleChunkRepository.META_VIDEO_NAME, file.getVideoName());
        metadata.put(SubtitleChunkRepository.META_FILE_ID, file.getFileId());
        metadata.put(SubtitleChunkRepository.META_FILE_INDEX, file.getFileIndex());
        metadata.put(SubtitleChunkRepository.META_START_SEC, (int) (startMs / 1000));
        metadata.put(SubtitleChunkRepository.META_END_SEC, (int) Math.ceil(endMs / 1000.0));
        metadata.put(SubtitleChunkRepository.META_LINES_START_SEC, (int) (linesStartMs / 1000));
        metadata.put(SubtitleChunkRepository.META_LINES, lines.toString());
        // id 固定下来，同一分P重复灌入会覆盖而不是重复
        return new Document(file.getFileId() + "-" + startMs, text.toString(), metadata);
    }

    private static void appendLine(StringBuilder lines, SubtitleCueDTO cue)
    {
        if (!lines.isEmpty())
        {
            lines.append('\n');
        }
        lines.append('[').append(formatTime((int) (cue.getStartMs() / 1000))).append("] ").append(cue.getText());
    }

    /**
     * 秒数格式化成 m:ss，超过一小时是 h:mm:ss
     */
    public static String formatTime(int seconds)
    {
        int hours = seconds / 3600;
        int minutes = seconds % 3600 / 60;
        int secs = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, secs) : String.format("%d:%02d", minutes, secs);
    }

    private static long toMs(Matcher matcher, int group)
    {
        return Long.parseLong(matcher.group(group)) * 3_600_000L + Long.parseLong(matcher.group(group + 1)) * 60_000L + Long.parseLong(
                matcher.group(group + 2)) * 1000L + Long.parseLong(matcher.group(group + 3));
    }
}
