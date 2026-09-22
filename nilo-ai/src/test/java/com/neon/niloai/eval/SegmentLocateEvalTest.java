package com.neon.niloai.eval;

import com.neon.niloai.entity.vo.CitedSegmentVO;
import com.neon.niloai.entity.vo.VideoAskVO;
import com.neon.niloai.repository.es.SubtitleChunkRepository;
import com.neon.niloai.service.SubtitleChunkService;
import com.neon.niloai.service.VideoAskService;
import com.neon.niloai.service.VideoVectorIndexService;
import com.neon.nilocommon.entity.dto.VideoFileSourceDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

/**
 * 片段定位评测（v5）<hr/>
 *
 * <p>灌入有字幕的已发布分P → 逐题检索、逐题问答 → 打印命中率。只出报告，不做断言。
 *
 * <p>主角是《Every Free App You Actually Need Explained in 20 Minutes》，简介里自带 11 个章节的时间戳，
 * 直接当标准答案；其余分P当全站检索的干扰项。验收标准按路线文档：跳转位置误差在 10 秒内。
 *
 * <p>字幕块是真实数据，跑完不删，和全量灌入的结果一样。需要 Nacos、ES、MinIO、模型服务都可用，在 IDEA 里手动运行。
 */
@Tag("eval")
@SpringBootTest
class SegmentLocateEvalTest
{
    private static final Long FREE_APP_VIDEO_ID = 2102331080186003456L;

    private static final int TOLERANCE_SEC = 10;

    /**
     * 格式：模式|问题|章节开始秒数|英文检索词。page 是在这个视频的页面上问，site 是在首页问。
     * 英文检索词是模型把问题改写后大概会用的词，用来对比「直接拿中文问题搜」和「改写成英文再搜」
     */
    private static final List <String> CASES = List.of("page|LibreOffice 是在哪儿讲的|0|LibreOffice",
                                                       "page|VLC 播放器在第几分钟|112|VLC media player",
                                                       "page|DaVinci Resolve 那段在哪|228|DaVinci Resolve",
                                                       "page|他讲 OBS 是在哪儿|329|OBS Studio",
                                                       "page|GIMP 在哪讲的|446|GIMP",
                                                       "page|Audacity 在哪|549|Audacity",
                                                       "page|Blender 那段从哪开始|658|Blender",
                                                       "page|Obsidian 在第几分钟|759|Obsidian",
                                                       "page|Scratch 在哪讲的|861|Scratch",
                                                       "page|Godot 在哪|953|Godot",
                                                       "page|HandBrake 在哪讲的|1056|HandBrake",
                                                       "page|讲免费替代 Office 的是哪段|0|free alternative to Microsoft Office",
                                                       "page|讲专业调色剪辑软件的在哪|228|professional video editing and color grading",
                                                       "page|讲直播推流软件的在哪|329|live streaming software",
                                                       "page|讲修图软件的在哪|446|photo editing software",
                                                       "page|讲录音、剪音频的在哪|549|recording and editing audio",
                                                       "page|讲做 3D 建模的在哪|658|3D modeling",
                                                       "page|讲记笔记软件的在哪|759|note taking app",
                                                       "page|讲教小孩编程的在哪|861|teach kids programming",
                                                       "page|讲做游戏的引擎在哪|953|game engine",
                                                       "site|哪个视频讲了 OBS 怎么用，在哪一段|329|OBS Studio",
                                                       "site|有没有视频讲 Blender，具体在哪|658|Blender",
                                                       "site|哪个视频提到了 Godot 游戏引擎，在几分钟|953|Godot game engine");

    @Autowired
    private SubtitleChunkService subtitleChunkService;

    @Autowired
    private SubtitleChunkRepository subtitleChunkRepository;

    @Autowired
    private VideoAskService videoAskService;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private VideoVectorIndexService videoVectorIndexService;

    @Test
    void evaluate() throws InterruptedException
    {
        // 视频索引还没有增量同步，新发布的视频要手动补一次，全站问答才搜得到
        if (Boolean.getBoolean("eval.reindexVideos"))
        {
            System.out.printf("视频索引全量灌入 %d 条%n", videoVectorIndexService.fullIndex());
        }
        List <VideoFileSourceDTO> files = fixtureFiles();
        files.stream().map(VideoFileSourceDTO::getVideoId).distinct().forEach(subtitleChunkRepository::deleteByVideoId);
        System.out.printf("灌入字幕块 %d 个%n", subtitleChunkService.indexFiles(files));
        // ES 默认 1 秒刷新一次，刚写入的块要等刷新后才搜得到
        Thread.sleep(2000);

        reportRetrieval("中文问题", 1);
        reportRetrieval("英文检索词", 3);
        if (!Boolean.getBoolean("eval.retrievalOnly"))
        {
            reportAnswer();
        }
    }

    /**
     * 只看检索：拿问题（或英文检索词）去搜字幕块，排第几的块覆盖了章节开头
     *
     * @param queryField 用用例的第几列当检索词
     */
    private void reportRetrieval(String label, int queryField)
    {
        int hitsAt1 = 0;
        int hitsAt3 = 0;
        StringBuilder misses = new StringBuilder();
        for (String evalCase : CASES)
        {
            String[] parts = evalCase.split("\\|");
            Long scope = "page".equals(parts[0]) ? FREE_APP_VIDEO_ID : null;
            int expected = Integer.parseInt(parts[2]);
            List <Document> documents = subtitleChunkRepository.search(parts[queryField], 3, scope);
            int rank = 0;
            for (int i = 0 ; i < documents.size() ; i++)
            {
                if (covers(documents.get(i), expected))
                {
                    rank = i + 1;
                    break;
                }
            }
            hitsAt1 += rank == 1 ? 1 : 0;
            hitsAt3 += rank > 0 ? 1 : 0;
            if (rank != 1)
            {
                misses.append(String.format("  ✗ %s  期望 %s  %s  实际:",
                                            parts[queryField],
                                            formatTime(expected),
                                            rank == 0 ? "前3未命中" : "排第" + rank));
                documents.forEach(document -> misses.append(' ')
                                                    .append(formatTime(Integer.parseInt(String.valueOf(document.getMetadata()
                                                                                                        .get(SubtitleChunkRepository.META_START_SEC)))))
                                                    .append(Long.valueOf(String.valueOf(document.getMetadata()
                                                                                          .get(SubtitleChunkRepository.META_VIDEO_ID)))
                                                                .equals(FREE_APP_VIDEO_ID) ? "" : "(别的视频)"));
                misses.append('\n');
            }
        }
        System.out.printf("[检索·%s] Hit@1 %d/%d  Hit@3 %d/%d%n", label, hitsAt1, CASES.size(), hitsAt3, CASES.size());
        System.out.print(misses);
    }

    /**
     * 端到端：走完整的问答，看回答给出的第一个片段离章节开头差多少秒
     */
    private void reportAnswer()
    {
        int within = 0;
        int noSegment = 0;
        StringBuilder details = new StringBuilder();
        for (String evalCase : CASES)
        {
            String[] parts = evalCase.split("\\|");
            Long scope = "page".equals(parts[0]) ? FREE_APP_VIDEO_ID : null;
            int expected = Integer.parseInt(parts[2]);
            String conversationId = "eval-" + UUID.randomUUID();
            VideoAskVO answer;
            try
            {
                answer = videoAskService.ask(parts[1], conversationId, scope);
            }
            finally
            {
                chatMemory.clear(conversationId);
            }
            CitedSegmentVO first = answer.getSegments()
                                         .stream()
                                         .filter(segment -> FREE_APP_VIDEO_ID.equals(segment.getVideoId()))
                                         .findFirst()
                                         .orElse(null);
            boolean ok = first != null && Math.abs(first.getStartSec() - expected) <= TOLERANCE_SEC;
            within += ok ? 1 : 0;
            noSegment += first == null ? 1 : 0;
            details.append(String.format("  %s [%s] %s  期望 %s  实际 %s%n",
                                         ok ? "✓" : "✗",
                                         parts[0],
                                         parts[1],
                                         formatTime(expected),
                                         first == null ? "无片段（" + answer.getIntent() + "）" : formatTime(first.getStartSec())));
            if (!ok)
            {
                details.append("      回答：").append(answer.getAnswer().replace('\n', ' ')).append('\n');
            }
        }
        System.out.printf("[问答] 误差≤%d秒 %d/%d  没给片段 %d%n", TOLERANCE_SEC, within, CASES.size(), noSegment);
        System.out.print(details);
    }

    /**
     * 块的时间范围覆盖了章节开头（开头往前放宽 10 秒）
     */
    private boolean covers(Document document, int expected)
    {
        if (!FREE_APP_VIDEO_ID.equals(Long.valueOf(String.valueOf(document.getMetadata().get(SubtitleChunkRepository.META_VIDEO_ID)))))
        {
            return false;
        }
        int start = Integer.parseInt(String.valueOf(document.getMetadata().get(SubtitleChunkRepository.META_START_SEC)));
        int end = Integer.parseInt(String.valueOf(document.getMetadata().get(SubtitleChunkRepository.META_END_SEC)));
        return start - TOLERANCE_SEC <= expected && expected <= end;
    }

    private String formatTime(int seconds)
    {
        return SubtitleChunkService.formatTime(seconds);
    }

    /**
     * 线上有字幕的已发布分P，从 video_info_file 里抄出来的
     */
    private List <VideoFileSourceDTO> fixtureFiles()
    {
        return List.of(file(2036995174466322432L, "DRAGONLADY", 2056220432914186240L, "20260518/7bcb0eb35f9ea0b3530c59ab71ea66", 88),
                       file(2036997411896819712L, "The Smartest RickRoll", 2036997411926179840L, "20260326/a785c49723dc140d40296722b5ebb9", 64),
                       file(2037345538440953856L, "error[redzone]", 2056217322607083520L, "20260518/6369d1eefc7a766afa10416dc9a71c", 112),
                       file(2037429716331266048L, "ALL MY FELLAS [SFM]", 2037429716423540736L, "20260327/1e31afabb48a7c320a9530c99d5e82", 75),
                       file(2037432604076015616L,
                            "LOW CORTISOL DANCE (Looped)",
                            2037432604101181440L,
                            "20260327/cd4a243baae4101a039e9997f90894",
                            121),
                       file(2037433409176862720L, "PowerShell in 100 Sekunden", 2037433409197834240L, "20260327/ecbd5d908d486b1cd86b567071ac43", 153),
                       file(2037434156232736768L,
                            "Rick Astley - Never Gonna Give You Up",
                            2037434156249513984L,
                            "20260327/c3c126c4e8d185a0d2e1e281618894",
                            213),
                       file(2037435252145651712L, "Steve Job's Goodbye Speech", 2037435252166623232L, "20260327/46306300b4b02a39770c7f2be8bf94", 110),
                       file(2038231166762352640L,
                            "All you need is ラプ(lapwing) VRChat",
                            2053759907924017152L,
                            "20260511/f299342413b492ebdcaf99d8ed723a",
                            93),
                       file(2038550324645462016L, "[SFM] Ugh, fiiiine...", 2038550324922286080L, "20260330/3ee6ddbae3c4671a6f36ed9424c508", 46),
                       file(2038596928152797184L, "Me after the lobotomy", 2038596928177963008L, "20260330/ae925694d80909ed85ba88489b90ed", 110),
                       file(2038597246739546112L, "Phones low battery sounds", 2038597246760517632L, "20260330/0ffa5bfe289d49a64491aa30295ea6", 503),
                       file(2038597705688678400L, "RUSH E", 2041036704877379584L, "20260406/1f99108678f23aaa326635daba68e8", 177),
                       file(2053376660035600384L, "KORORIN", 2053376660048183296L, "20260510/65e8ce47fa2022bacaf9e56c53d2b1", 128),
                       file(2054364617605382144L,
                            "RemoteCompose for Android Widgets Across Form Factors",
                            2054364617890594816L,
                            "20260513/67fbe0dc9b1bf1e81ddcb8f29ce998",
                            106),
                       file(2063458803822624768L,
                            "Gabe Newell answered fan's email",
                            2063458803877150720L,
                            "20260607/9b85bdf0591e4cc385e72619765398",
                            32),
                       file(FREE_APP_VIDEO_ID,
                            "Every Free App You Actually Need Explained in 20 Minutes",
                            2102331080274083840L,
                            "20260922/g8rfs1cDNhaK5184DePpUHcd8VBdDd",
                            1219));
    }

    private VideoFileSourceDTO file(Long videoId, String videoName, Long fileId, String filePath, int duration)
    {
        return new VideoFileSourceDTO(videoId, videoName, fileId, 1, filePath, duration);
    }
}
