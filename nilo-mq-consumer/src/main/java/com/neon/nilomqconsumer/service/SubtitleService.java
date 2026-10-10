package com.neon.nilomqconsumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.subtitle.SubtitleChapterDTO;
import com.neon.nilocommon.entity.dto.subtitle.SubtitleSummaryDTO;
import com.neon.nilocommon.entity.enums.subtitle.SubtitleResult;
import com.neon.nilocommon.util.FfmpegUtil;
import com.neon.nilomqconsumer.config.properties.AsrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字幕生成：把视频里说的话识别成带时间的 SRT 字幕<hr/>
 * <p>用的是 AssemblyAI 的异步转写：音频先传到它的存储拿到地址，再提交识别拿到任务 id，之后轮询到结束再取分好句的结果。</p>
 * <p>分成 {@link #submit} 和 {@link #writeSrt} 两步，是为了让云端识别和本地转码同时进行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubtitleService
{
    /**
     * 抽出来的音频文件名。提交完就删，不能留在转码目录里，否则会跟着上传到 MinIO
     */
    private static final String AUDIO_NAME = "audio.m4a";

    /**
     * 打开自动识别语种后，音频里没有人声时任务报的错误信息里带这句
     */
    private static final String NO_SPEECH_ERROR = "no spoken audio";

    /**
     * 拆转化后的文本的词：前面是词本身，结尾连着的标点单独拿出来
     */
    private static final Pattern WORD_PUNCTUATION = Pattern.compile("^(.*?)(\\p{P}*)$");

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);

    /**
     * 转码结束后最多再等多久。识别和转码是同时跑的，正常情况下转码完识别也早就好了
     */
    private static final Duration MAX_WAIT = Duration.ofMinutes(10);

    /**
     * 一行字幕最多多宽，超过就断行。宽度按显示算：汉字、假名、全角标点算 2，字母、数字、空格算 1，
     * 所以中文一行约 21 个字，英文一行约 7、8 个单词
     */
    private static final int LINE_MAX_WIDTH = 42;

    /**
     * 一行攒到多宽之后，遇到标点就断行。太短就断会满屏闪短句
     */
    private static final int LINE_MIN_WIDTH = 16;

    /**
     * 句子最后只剩这么窄一截时不单独成行，挤进上一行，免得最后一两个词单独闪一下
     */
    private static final int TAIL_MAX_WIDTH = 8;

    /**
     * 字幕习惯：行尾的逗号、句号这类标点不显示，问号叹号保留
     */
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[，。、；：,.;:\\s]+$");

    /**
     * 拆译文用：连续的字母数字算一个词，空白算一段，其它字符一个一个来
     */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9]+|\\s+|.");

    /**
     * 标点和空白不单独算词，挂在前一个词后面
     */
    private static final Pattern ATTACHED_TOKEN = Pattern.compile("[\\p{P}\\s]+");

    private final RestClient asrRestClient;

    private final AsrProperties asrProperties;

    private final ObjectMapper objectMapper;

    private final SubtitleLlmService subtitleLlmService;

    /**
     * 抽出音轨并提交识别任务
     *
     * @param videoPath 原视频路径
     * @return 识别任务 id；视频没有音轨时返回 null
     */
    public String submit(Path videoPath)
    {
        if (!FfmpegUtil.hasAudioStream(videoPath.toString()))
        {
            log.info("视频没有音轨，跳过字幕识别, videoPath={}", videoPath);
            return null;
        }

        Path audioPath = videoPath.resolveSibling(AUDIO_NAME);
        try
        {
            FfmpegUtil.extractAudio(videoPath.toString(), audioPath.toString(), false);
            // 先上传音频文件
            String audioUrl = uploadAudio(audioPath);
            // 然后开始音频识别任务
            String taskId = submitTask(audioUrl);
            log.info("字幕识别任务已提交, taskId={}, videoPath={}", taskId, videoPath);
            return taskId;
        }
        finally
        {
            try
            {
                Files.deleteIfExists(audioPath);
            }
            catch (IOException e)
            {
                log.warn("删除本地音频失败，它会跟着转码目录一起上传, audioPath={}", audioPath, e);
            }
        }
    }

    /**
     * 等识别任务结束，把结果写成 SRT 字幕文件<hr/>
     * <p>
     * 写之前先让大模型判断识别结果是不是乱码（唱歌、背景音乐很强时常见），乱码就不生成；
     * 判断这一步调用失败则保留字幕，之后靠用户反馈修正。
     * </p>
     * <p>原文不是中文时，再让大模型逐行翻译一份中文字幕，放在同一目录；翻译失败就只保留原文。</p>
     *
     * @param taskId       {@link #submit} 返回的任务 id
     * @param subtitlePath 原文字幕保存路径，中文翻译放在同一目录下的 {@link Constants#SUBTITLE_ZH_NAME}
     * @return 生成结果
     */
    public SubtitleResult writeSrt(String taskId, Path subtitlePath)
    {
        Path chinesePath = subtitlePath.resolveSibling(Constants.SUBTITLE_ZH_NAME);
        Path summaryPath = subtitlePath.resolveSibling(Constants.SUMMARY_NAME);

        // 先清掉上次留下的文件。乱码、没人声、原文已是中文时不再生成对应文件，
        // 否则旧字幕会跟着转码目录一起上传
        deleteIfExists(subtitlePath);
        deleteIfExists(chinesePath);
        deleteIfExists(summaryPath);

        if (!waitForResult(taskId))
        {
            log.info("视频里没有识别出语音，不生成字幕, taskId={}", taskId);
            return SubtitleResult.NO_SPEECH;
        }

        JsonNode sentences = fetchTranscriptToSentences(taskId);
        List <String> cues = toCues(sentences);
        if (cues.isEmpty())
        {
            log.info("识别结果里没有句子，不生成字幕, taskId={}", taskId);
            return SubtitleResult.NO_SPEECH;
        }

        // 质检和翻译都按整句来：切碎后的短行单独看像乱码，按行翻译模型也会自作主张把几行并成一句
        List <String> sentenceTexts = new ArrayList <>(sentences.size());
        for (JsonNode sentence : sentences)
        {
            sentenceTexts.add(sentence.path("text").asText());
        }
        if (!isReadable(taskId, sentenceTexts))
        {
            log.info("识别结果被判定为乱码，不生成字幕, taskId={}", taskId);
            return SubtitleResult.UNREADABLE;
        }
        writeFile(subtitlePath, cues);
        log.info("字幕生成成功, taskId={}, lines={}, subtitlePath={}", taskId, cues.size(), subtitlePath);

        // 字幕生成完毕后，让大模型根据字幕写出总结
        writeSummary(taskId, summaryPath, sentences, sentenceTexts);

        if (isChinese(sentenceTexts))
        {
            return SubtitleResult.CHINESE;
        }

        List <String> translated;
        try
        {
            translated = subtitleLlmService.translateToChinese(sentenceTexts);
        }
        catch (RuntimeException e)
        {
            log.warn("翻译字幕失败，只保留原文字幕, taskId={}", taskId, e);
            return SubtitleResult.TRANSLATE_FAILED;
        }

        List <String> chineseCues = new ArrayList <>();
        for (int i = 0 ; i < sentences.size() ; i++)
        {
            JsonNode sentence = sentences.get(i);
            String text = i < translated.size() ? translated.get(i) : null;
            // 模型偶尔漏翻个别句子，就用原文顶上
            text = StringUtils.hasText(text) ? text.replace('\n', ' ').trim() : sentenceTexts.get(i);
            splitTranslatedSentence(chineseCues, text, sentence.path("begin_time").asLong(), sentence.path("end_time").asLong());
        }
        writeFile(chinesePath, chineseCues);
        log.info("中文翻译字幕生成成功, taskId={}, lines={}", taskId, chineseCues.size());
        return SubtitleResult.TRANSLATED;
    }

    /**
     * 让大模型总结这一P讲了什么并切出章节，结果写成 summary.json<hr/>
     * <p>转码时算一次存起来，用户看的时候前端直接读文件，不用每次都让模型现算。</p>
     * <p>失败只记日志：总结是附加内容，不值得让整条转码流程重来。</p>
     */
    private void writeSummary(String taskId, Path summaryPath, JsonNode sentences, List <String> sentenceTexts)
    {
        try
        {
            SubtitleSummaryDTO summary = subtitleLlmService.summarize(toTimedLines(sentences, sentenceTexts));
            summary.setChapters(alignChapters(summary.getChapters(), sentences));
            writeFile(summaryPath, objectMapper.writeValueAsString(summary));
            log.info("视频总结生成成功, taskId={}, chapters={}", taskId, summary.getChapters().size());
        }
        catch (RuntimeException | IOException e)
        {
            log.warn("生成视频总结失败，跳过, taskId={}", taskId, e);
        }
    }

    /**
     * 拼成「[秒数] 台词」一行一句，行首直接给秒数，省得模型自己换算时间
     */
    private static String toTimedLines(JsonNode sentences, List <String> sentenceTexts)
    {
        StringBuilder lines = new StringBuilder();
        for (int i = 0 ; i < sentenceTexts.size() ; i++)
        {
            if (!lines.isEmpty())
            {
                lines.append('\n');
            }
            lines.append('[')
                 .append(sentences.get(i).path("begin_time").asLong() / 1000)
                 .append("] ")
                 .append(sentenceTexts.get(i));
        }
        return lines.toString();
    }

    /**
     * 把模型给的章节时间吸附到最近一句台词的开始时间<hr/>
     * 模型偶尔会给一个字幕里不存在的秒数，跳过去就落在半句话中间；超出字幕范围、标题为空、时间重复的一并丢掉
     */
    private static List <SubtitleChapterDTO> alignChapters(List <SubtitleChapterDTO> chapters, JsonNode sentences)
    {
        List <SubtitleChapterDTO> aligned = new ArrayList <>();
        if (chapters == null)
        {
            return aligned;
        }
        long lastSec = sentences.get(sentences.size() - 1).path("end_time").asLong() / 1000;
        Set <Integer> used = new HashSet <>();
        for (SubtitleChapterDTO chapter : chapters)
        {
            if (chapter == null || chapter.getStartSec() == null || !StringUtils.hasText(chapter.getTitle()) || chapter.getStartSec() < 0 || chapter.getStartSec() > lastSec)
            {
                continue;
            }
            int startSec = nearestSentenceSec(sentences, chapter.getStartSec());
            if (used.add(startSec))
            {
                aligned.add(new SubtitleChapterDTO(startSec, chapter.getTitle().trim()));
            }
        }
        aligned.sort(Comparator.comparing(SubtitleChapterDTO::getStartSec));
        return aligned;
    }

    private static int nearestSentenceSec(JsonNode sentences, int startSec)
    {
        int nearest = 0;
        long minGap = Long.MAX_VALUE;
        for (JsonNode sentence : sentences)
        {
            int sec = (int) (sentence.path("begin_time").asLong() / 1000);
            long gap = Math.abs(sec - startSec);
            if (gap < minGap)
            {
                minGap = gap;
                nearest = sec;
            }
        }
        return nearest;
    }

    /**
     * 让大模型判断字幕能不能读懂<hr/>
     * 调用失败按「能读懂」处理：宁可先留着让用户反馈，也不因为判断失败丢掉字幕
     */
    private boolean isReadable(String taskId, List <String> texts)
    {
        try
        {
            return subtitleLlmService.isReadable(texts);
        }
        catch (RuntimeException e)
        {
            log.warn("字幕质检调用失败，先保留字幕, taskId={}", taskId, e);
            return true;
        }
    }

    /**
     * 粗略判断字幕是不是中文：汉字比拉丁字母多（一个汉字顶两个字母），并且假名比汉字少（排除日文）
     */
    private boolean isChinese(List <String> texts)
    {
        int han = 0;
        int kana = 0;
        int latin = 0;
        for (String text : texts)
        {
            for (int codePoint : text.codePoints().toArray())
            {
                Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                if (script == Character.UnicodeScript.HAN)
                {
                    han++;
                }
                else if (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA)
                {
                    kana++;
                }
                else if (script == Character.UnicodeScript.LATIN)
                {
                    latin++;
                }
            }
        }
        return han * 2 >= latin && kana < han;
    }

    /**
     * 给字幕编上序号，写成 SRT 文件
     */
    private void writeFile(Path path, List <String> cues)
    {
        StringBuilder srt = new StringBuilder();
        for (int i = 0 ; i < cues.size() ; i++)
        {
            srt.append(i + 1).append('\n').append(cues.get(i)).append("\n\n");
        }
        writeFile(path, srt.toString());
    }

    private void writeFile(Path path, String content)
    {
        try
        {
            Path parent = path.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("写入字幕文件失败, path=" + path, e);
        }
    }

    private void deleteIfExists(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("删除旧字幕失败, path=" + path, e);
        }
    }

    /**
     * 把本地音频传到 AI 提供商的云存储空间<hr/>
     * 按文件流式上传，不整个读进内存；请求头会带上文件长度
     *
     * @return 上传后的地址，只有 AI 提供商自己能访问，提交识别时用
     */
    private String uploadAudio(Path filePath)
    {
        JsonNode response = asrRestClient.post()
                                         .uri(URI.create(asrProperties.getBaseUrl() + "/v2/upload"))
                                         .header(HttpHeaders.AUTHORIZATION, asrProperties.getApiKey())
                                         .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                         .body(new FileSystemResource(filePath))
                                         .retrieve()
                                         .body(JsonNode.class);

        String uploadUrl = response == null ? null : response.path("upload_url").asText(null);
        if (!StringUtils.hasText(uploadUrl))
        {
            throw new IllegalStateException("上传音频没有返回 upload_url, response=" + response);
        }
        return uploadUrl;
    }

    /**
     * 提交异步识别任务
     *
     * @param audioUrl 音频地址
     * @return 任务 id
     */
    private String submitTask(String audioUrl)
    {
        // 不限定语种，打开自动识别：站内视频语种不固定，限定成某种语言时，其他语言会被识别成乱码，后面的质检会把整份字幕丢掉
        Map <String, Object> body = Map.of("audio_url",
                                           audioUrl,
                                           "speech_models",
                                           asrProperties.getSpeechModels(),
                                           "language_detection",
                                           true);

        JsonNode response = asrRestClient.post()
                                         .uri(URI.create(asrProperties.getBaseUrl() + "/v2/transcript"))
                                         .header(HttpHeaders.AUTHORIZATION, asrProperties.getApiKey())
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .body(body)
                                         .retrieve()
                                         .body(JsonNode.class);

        String taskId = response == null ? null : response.path("id").asText(null);
        if (!StringUtils.hasText(taskId))
        {
            throw new IllegalStateException("提交识别任务没有返回 id, response=" + response);
        }
        return taskId;
    }

    /**
     * 获取转换后的文本，并标准化分词格式<hr/>
     * <p>标准化后的结构如下，时间单位是毫秒。把每个词的 text 和 punctuation 依次首尾相接，就能还原出整句：
     * {@code "Hello" + " " + "world" + "." = "Hello world."}</p>
     * <pre>
     * [
     *   {
     *     "text": "Hello world.",
     *     "begin_time": 1000,
     *     "end_time": 2500,
     *     "words": [
     *       {
     *         "text": "Hello",
     *         "punctuation": " ",
     *         "begin_time": 1000,
     *         "end_time": 1400
     *       },
     *       {
     *         "text": "world",
     *         "punctuation": ".",
     *         "begin_time": 1400,
     *         "end_time": 2500
     *       }
     *     ]
     *   }
     * ]
     * </pre>
     * <p>每个词的标准化规则：</p>
     * <ul>
     *     <li>text：去掉结尾标点后的词本身。词中间的符号保留，比如 mid-20s、wouldn't</li>
     *     <li>punctuation：词结尾连着的标点，再加上整句文本里紧跟在这个词后面的空格。
     *     对位方法是在整句里按顺序往后找这个词，找不到就只放标点、不补空格</li>
     * </ul>
     * <p>举例：</p>
     * <ul>
     *     <li>"ago," 后面有空格 → text = "ago"，punctuation = ", "</li>
     *     <li>"some" 后面有空格 → text = "some"，punctuation = " "</li>
     *     <li>句末的 "transplant." → text = "transplant"，punctuation = "."</li>
     *     <li>中文的 "上，" → text = "上"，punctuation = "，"</li>
     *     <li>中文里夹的英文有时会被切碎（DOTA 切成 D、OT、A），整句里它们连在一起，三个词的 punctuation 都是空串</li>
     * </ul>
     * <p>空格以整句文本为准，不按字符类型猜：英文词间有空格，中文里夹的英文没有，猜的话会把 DOTA 拼成 "D OT A"。</p>
     * <p>下游怎么用：断行时只把含有可见字符的 punctuation 当作可以断开的位置，纯空格不算，所以英文不会逐词断行；
     * 每行字幕输出时再去掉行尾的逗号、句号这类标点和空格，见 {@link #addCue}。</p>
     */
    private ArrayNode fetchTranscriptToSentences(String taskId)
    {
        JsonNode response = asrRestClient.get()
                                         .uri(URI.create(asrProperties.getBaseUrl() + "/v2/transcript/" + taskId + "/sentences"))
                                         .header(HttpHeaders.AUTHORIZATION, asrProperties.getApiKey())
                                         .retrieve()
                                         .body(JsonNode.class);

        ArrayNode sentences = objectMapper.createArrayNode();
        if (response == null)
        {
            return sentences;
        }
        for (JsonNode source : response.path("sentences"))
        {
            String sentenceText = source.path("text").asText();
            ObjectNode sentence = sentences.addObject()
                                           .put("text", sentenceText)
                                           .put("begin_time", source.path("start").asLong())
                                           .put("end_time", source.path("end").asLong());
            ArrayNode words = sentence.putArray("words");
            // 整句文本里已经对到哪了，下一个词从这里往后找
            int cursor = 0;
            for (JsonNode word : source.path("words"))
            {
                String wordText = word.path("text").asText();
                String prefixText = wordText;
                String punctuation = "";
                Matcher matcher = WORD_PUNCTUATION.matcher(wordText);
                if (matcher.matches())
                {
                    prefixText = matcher.group(1);
                    punctuation = matcher.group(2);
                }

                // 找不到就不补空格，也不挪位置，后面的词照常往后找
                int index = sentenceText.indexOf(wordText, cursor);
                if (index >= 0)
                {
                    cursor = index + wordText.length();
                    int spaceEnd = cursor;
                    while (spaceEnd < sentenceText.length() && Character.isWhitespace(sentenceText.charAt(spaceEnd)))
                    {
                        spaceEnd++;
                    }
                    punctuation += sentenceText.substring(cursor, spaceEnd);
                    cursor = spaceEnd;
                }

                words.addObject()
                     .put("text", prefixText)
                     .put("punctuation", punctuation)
                     .put("begin_time", word.path("start").asLong())
                     .put("end_time", word.path("end").asLong());
            }
        }
        return sentences;
    }

    /**
     * 轮询任务直到结束
     *
     * @return 识别完成返回 true；音频里没有人声返回 false
     */
    private boolean waitForResult(String taskId)
    {
        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (true)
        {
            JsonNode transcript;
            try
            {
                transcript = asrRestClient.get()
                                          .uri(URI.create(asrProperties.getBaseUrl() + "/v2/transcript/" + taskId))
                                          .header(HttpHeaders.AUTHORIZATION, asrProperties.getApiKey())
                                          .retrieve()
                                          .body(JsonNode.class);
            }
            catch (ResourceAccessException e)
            {
                // 网络出错不放弃，识别任务还在云端跑，等一会儿再问。
                // 典型情况是转码期间连接闲置太久被对方关掉，复用时读到 EOF；出错的连接会被丢掉，下一轮会新建连接
                log.warn("查询识别结果时网络出错，稍后重试, taskId={}, error={}", taskId, e.getMessage());
                waitNextPoll(taskId, deadline);
                continue;
            }

            String status = transcript == null ? "" : transcript.path("status").asText();
            if ("queued".equals(status) || "processing".equals(status))
            {
                waitNextPoll(taskId, deadline);
            }
            else if ("completed".equals(status))
            {
                // 打出实际用的模型和识别出的语种，接口里模型名写错、语种判断不对时一眼能看出来
                log.info("字幕识别完成, taskId={}, model={}, language={}",
                         taskId,
                         transcript.path("speech_model_used").asText(),
                         transcript.path("language_code").asText());
                return true;
            }
            else
            {
                // 打开了自动识别语种，音频里没有人声（比如纯音乐、静音）时任务会直接报错，不算失败
                String error = transcript == null ? "" : transcript.path("error").asText();
                if (error.contains(NO_SPEECH_ERROR))
                {
                    return false;
                }
                else
                {
                    throw new IllegalStateException("识别失败, taskId=" + taskId + ", transcript=" + transcript);
                }
            }
        }
    }

    /**
     * 等到下一次轮询<hr/>
     * 已经超过总期限就不再等，直接报超时
     *
     * @param deadline 总期限，System.nanoTime() 的时刻
     */
    private void waitNextPoll(String taskId, long deadline)
    {
        if (System.nanoTime() > deadline)
        {
            throw new IllegalStateException("等待音频转文本识别结果超时, taskId=" + taskId);
        }
        try
        {
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待识别结果时被中断, taskId=" + taskId, e);
        }
    }

    /**
     * 把识别出的句子切成一条条字幕
     *
     * @return 每条字幕是「时间轴 + 换行 + 文字」，还没有序号
     */
    private List <String> toCues(JsonNode sentences)
    {
        List <String> cues = new ArrayList <>();
        for (JsonNode sentence : sentences)
        {
            JsonNode words = sentence.path("words");
            if (words.isEmpty())
            {
                addCue(cues,
                       sentence.path("begin_time").asLong(),
                       sentence.path("end_time").asLong(),
                       sentence.path("text").asText());
            }
            else
            {
                splitSentence(cues, words);
            }
        }
        return cues;
    }

    /**
     * 切分句子<hr/>
     * <p>按每个词自己的时间，把一句话切成几条字幕</p>
     * <p>识别按说话停顿断句，一句可能有二三十个字。宽度攒到 {@link #LINE_MIN_WIDTH} 以后遇到标点就断，
     * 最宽不超过 {@link #LINE_MAX_WIDTH}，句尾太短的一截挤进上一行。</p>
     *
     * @param words 这句话的词，每个词有 text、punctuation、begin_time、end_time
     */
    private void splitSentence(List <String> cues, JsonNode words)
    {
        // 这句话从当前词往后（含当前词）还剩多宽，用来判断会不会留下太短的尾巴
        int remainingWidth = 0;
        for (JsonNode word : words)
        {
            remainingWidth += displayWidth(word.path("text").asText() + word.path("punctuation").asText());
        }

        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        long lineStart = 0;
        long lineEnd = 0;
        for (JsonNode word : words)
        {
            String punctuation = word.path("punctuation").asText();
            String text = word.path("text").asText() + punctuation;
            int textWidth = displayWidth(text);

            // 再加这个词就超宽了，先把攒着的这行输出；句子只剩很短一截时例外，直接挤进这一行
            if (!line.isEmpty() && lineWidth + textWidth > LINE_MAX_WIDTH && remainingWidth > TAIL_MAX_WIDTH)
            {
                addCue(cues, lineStart, lineEnd, line.toString());
                line.setLength(0);
                lineWidth = 0;
            }

            if (line.isEmpty())
            {
                lineStart = word.path("begin_time").asLong();
            }
            line.append(text);
            lineWidth += textWidth;
            remainingWidth -= textWidth;
            lineEnd = word.path("end_time").asLong();

            // 这行已经不短了，又正好停在标点上，在这里断开读起来最自然；同样不给后面留太短的尾巴
            if (StringUtils.hasText(punctuation) && lineWidth >= LINE_MIN_WIDTH && remainingWidth > TAIL_MAX_WIDTH)
            {
                addCue(cues, lineStart, lineEnd, line.toString());
                line.setLength(0);
                lineWidth = 0;
            }
        }
        if (!line.isEmpty())
        {
            addCue(cues, lineStart, lineEnd, line.toString());
        }
    }

    /**
     * 把一句译文切成几条字幕<hr/>
     * <p>译文没有逐字的时间，所以先拆成一个个「词」：连续的字母数字算一个词，汉字一个字一个词，
     * 标点和空格挂在前一个词后面；再按宽度在原句的时间段里平均分配时间，交给 {@link #splitSentence} 用和原文一样的规则断行。</p>
     *
     * @param begin 原句开始时间（毫秒）
     * @param end   原句结束时间（毫秒）
     */
    private void splitTranslatedSentence(List <String> cues, String text, long begin, long end)
    {
        ArrayNode words = objectMapper.createArrayNode();
        int totalWidth = Math.max(1, displayWidth(text));
        int widthBefore = 0;
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find())
        {
            String token = matcher.group();
            int width = displayWidth(token);
            if (ATTACHED_TOKEN.matcher(token).matches() && !words.isEmpty())
            {
                ObjectNode last = (ObjectNode) words.get(words.size() - 1);
                last.put("punctuation", last.path("punctuation").asText() + token);
            }
            else
            {
                words.addObject()
                     .put("text", token)
                     .put("punctuation", "")
                     .put("begin_time", begin + (end - begin) * widthBefore / totalWidth)
                     .put("end_time", begin + (end - begin) * (widthBefore + width) / totalWidth);
            }
            widthBefore += width;
        }
        if (!words.isEmpty())
        {
            splitSentence(cues, words);
        }
    }

    /**
     * 添加一条字幕<hr/>
     * 自动处理结尾符号
     *
     * @param cues    字幕列表
     * @param startMs 开始时间，毫秒
     * @param endMs   结束时间，毫秒
     * @param text    字幕文本
     */
    private void addCue(List <String> cues, long startMs, long endMs, String text)
    {
        String cleaned = TRAILING_PUNCTUATION.matcher(text).replaceAll("").trim();
        if (cleaned.isEmpty())
        {
            return;
        }
        cues.add(srtTime(startMs) + " --> " + srtTime(endMs) + "\n" + cleaned);
    }

    /**
     * 文字在屏幕上占多宽：U+2E80 之后的汉字、假名、韩文、全角标点算 2，字母、数字、空格这些算 1
     */
    private int displayWidth(String text)
    {
        return text.codePoints().map(codePoint -> codePoint >= 0x2E80 ? 2 : 1).sum();
    }

    /**
     * 毫秒转成 SRT 的时间格式 00:01:02,345
     */
    private String srtTime(long ms)
    {
        return "%02d:%02d:%02d,%03d".formatted(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000);
    }
}
