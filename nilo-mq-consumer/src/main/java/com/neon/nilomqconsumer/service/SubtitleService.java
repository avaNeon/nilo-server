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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字幕生成：把视频里说的话识别成带时间的 SRT 字幕<hr/>
 * <p>用的是阿里云百炼的录音文件识别，它只认公网地址，所以音频先传到百炼的免费临时存储（48 小时有效），
 * 拿到 oss:// 地址再提交识别。识别是异步的：提交拿到任务 id，之后轮询到结束再下载结果。</p>
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
     * 申请临时存储上传凭证的地址。官方文档只给了这个通用域名，没给业务空间专属域名的写法
     */
    private static final String UPLOAD_POLICY_URL = "https://dashscope.aliyuncs.com/api/v1/uploads?action=getPolicy&model=";

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
            String fileUrl = uploadTempFile(audioPath);
            String taskId = submitTask(fileUrl);
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
     * <p>写之前先让大模型判断识别结果是不是乱码（唱歌、背景音乐很强时常见），乱码就不生成；
     * 判断这一步调用失败则保留字幕，之后靠用户反馈修正。</p>
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

        String transcriptionUrl = waitForResult(taskId);
        if (transcriptionUrl == null)
        {
            log.info("视频里没有识别出语音，不生成字幕, taskId={}", taskId);
            return SubtitleResult.NO_SPEECH;
        }

        // 结果地址是带签名的 OSS 链接，必须原样使用，不能按模板再编码一遍；
        // 用 byte[] 接收再交给 Jackson，避免响应头没写字符集时中文按 ISO-8859-1 解码成乱码
        byte[] resultBytes = asrRestClient.get().uri(URI.create(transcriptionUrl)).retrieve().body(byte[].class);
        JsonNode sentences;
        try
        {
            // 抽音频时转成了单声道，只会有一条 transcript
            sentences = objectMapper.readTree(resultBytes).path("transcripts").path(0).path("sentences");
        }
        catch (IOException e)
        {
            throw new IllegalStateException("解析识别结果失败, taskId=" + taskId, e);
        }
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
            lines.append('[').append(sentences.get(i).path("begin_time").asLong() / 1000).append("] ").append(sentenceTexts.get(i));
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
            if (chapter == null || chapter.getStartSec() == null || !StringUtils.hasText(chapter.getTitle()) || chapter.getStartSec() < 0
                || chapter.getStartSec() > lastSec)
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
     * 把本地文件传到百炼的临时存储<hr/>
     * 先申请上传凭证，再按凭证直传 OSS
     *
     * @return oss:// 开头的临时地址，48 小时有效
     */
    private String uploadTempFile(Path file)
    {
        // 凭证申请时的 model 必须和识别时用的一致，否则识别时读不到这个文件；凭证 5 分钟过期，所以每次上传前现申请
        JsonNode policy = asrRestClient.get()
                                       .uri(URI.create(UPLOAD_POLICY_URL + asrProperties.getModel()))
                                       .header(HttpHeaders.AUTHORIZATION, bearer())
                                       .retrieve()
                                       .body(JsonNode.class)
                                       .path("data");

        // 超过临时存储的大小上限就不传了：上传时整个文件要先读进内存，特别长的音频可能把内存撑爆
        long maxBytes = policy.path("max_file_size_mb").asLong() * Constants.Mebibyte;
        long fileBytes;
        try
        {
            fileBytes = Files.size(file);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("读取音频大小失败, file=" + file, e);
        }
        if (maxBytes > 0 && fileBytes > maxBytes)
        {
            throw new IllegalStateException("音频超过临时存储上限，跳过字幕, fileBytes=" + fileBytes + ", maxBytes=" + maxBytes);
        }

        // 临时存储禁止覆盖同名文件，文件名用 UUID 保证不重复
        String key = policy.path("upload_dir").asText() + "/" + UUID.randomUUID() + ".m4a";

        // LinkedMultiValueMap 保持放入顺序，OSS 要求 file 必须是最后一个字段
        MultiValueMap <String, Object> form = new LinkedMultiValueMap <>();
        form.add("OSSAccessKeyId", policy.path("oss_access_key_id").asText());
        form.add("Signature", policy.path("signature").asText());
        form.add("policy", policy.path("policy").asText());
        form.add("key", key);
        form.add("x-oss-object-acl", policy.path("x_oss_object_acl").asText());
        form.add("x-oss-forbid-overwrite", policy.path("x_oss_forbid_overwrite").asText());
        form.add("success_action_status", "200");
        form.add("file", new FileSystemResource(file));

        asrRestClient.post()
                     .uri(URI.create(policy.path("upload_host").asText()))
                     .contentType(MediaType.MULTIPART_FORM_DATA)
                     .body(form)
                     .retrieve()
                     .toBodilessEntity();

        return "oss://" + key;
    }

    /**
     * 提交异步识别任务
     *
     * @param fileUrl 音频地址
     * @return 任务 id
     */
    private String submitTask(String fileUrl)
    {
        // 不传 language_hints：站内视频语种不固定，paraformer-v2 会自行判断。
        // 限定成中英时，日语、韩语等会被识别成乱码，后面的质检会把整份字幕丢掉
        Map <String, Object> body = Map.of("model",
                                           asrProperties.getModel(),
                                           "input",
                                           Map.of("file_urls", List.of(fileUrl)),
                                           "parameters",
                                           Map.of("timestamp_alignment_enabled", true));

        JsonNode response = asrRestClient.post()
                                         .uri(URI.create(asrProperties.getBaseUrl() + "/api/v1/services/audio/asr/transcription"))
                                         .header(HttpHeaders.AUTHORIZATION, bearer())
                                         // 异步提交
                                         .header("X-DashScope-Async", "enable")
                                         // 允许使用 oss:// 临时地址
                                         .header("X-DashScope-OssResourceResolve", "enable")
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .body(body)
                                         .retrieve()
                                         .body(JsonNode.class);

        String taskId = response == null ? null : response.path("output").path("task_id").asText(null);
        if (!StringUtils.hasText(taskId))
        {
            throw new IllegalStateException("提交识别任务没有返回 task_id, response=" + response);
        }
        return taskId;
    }

    /**
     * 轮询任务直到结束
     *
     * @return 识别结果的下载地址；音频里没有语音时返回 null
     */
    private String waitForResult(String taskId)
    {
        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (true)
        {
            JsonNode output = asrRestClient.get()
                                           .uri(URI.create(asrProperties.getBaseUrl() + "/api/v1/tasks/" + taskId))
                                           .header(HttpHeaders.AUTHORIZATION, bearer())
                                           .retrieve()
                                           .body(JsonNode.class)
                                           .path("output");

            String status = output.path("task_status").asText();
            if ("PENDING".equals(status) || "RUNNING".equals(status))
            {
                if (System.nanoTime() > deadline)
                {
                    throw new IllegalStateException("等待识别结果超时, taskId=" + taskId);
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
                continue;
            }

            // 任务结束了。一次只提交了一个文件，只有一条结果；任务整体可能是 SUCCEEDED 也可能是 FAILED，以这条结果为准
            JsonNode result = output.path("results").path(0);
            if ("SUCCEEDED".equals(result.path("subtask_status").asText()))
            {
                return result.path("transcription_url").asText();
            }
            // 音频里没有有效语音（比如纯音乐、静音），不算失败
            String code = result.path("code").asText();
            if ("SUCCESS_WITH_NO_VALID_FRAGMENT".equals(code) || "ASR_RESPONSE_HAVE_NO_WORDS".equals(code))
            {
                return null;
            }
            throw new IllegalStateException("识别失败, taskId=" + taskId + ", output=" + output);
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
     * 按每个词自己的时间，把一句话切成几条字幕<hr/>
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

    private String bearer()
    {
        return "Bearer " + asrProperties.getApiKey();
    }
}
