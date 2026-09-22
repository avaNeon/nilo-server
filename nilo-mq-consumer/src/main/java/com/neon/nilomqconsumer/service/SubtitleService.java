package com.neon.nilomqconsumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neon.nilocommon.entity.constants.Constants;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
     * 一行字幕最多多少个字，超过就断行
     */
    private static final int LINE_MAX_CHARS = 20;

    /**
     * 一行攒到多少个字之后，遇到标点就断行。太短就断会满屏闪短句
     */
    private static final int LINE_MIN_CHARS = 8;

    /**
     * 字幕习惯：行尾的逗号、句号这类标点不显示，问号叹号保留
     */
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[，。、；：,.;:\\s]+$");

    private final RestClient asrRestClient;

    private final AsrProperties asrProperties;

    private final ObjectMapper objectMapper;

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
     * 等识别任务结束，把结果写成 SRT 字幕文件。视频里一句话都没识别出来时不生成文件
     *
     * @param taskId       {@link #submit} 返回的任务 id
     * @param subtitlePath 字幕文件保存路径
     */
    public void writeSrt(String taskId, Path subtitlePath)
    {
        String transcriptionUrl = waitForResult(taskId);
        if (transcriptionUrl == null)
        {
            log.info("视频里没有识别出语音，不生成字幕, taskId={}", taskId);
            return;
        }

        // 结果地址是带签名的 OSS 链接，必须原样使用，不能按模板再编码一遍；
        // 用 byte[] 接收再交给 Jackson，避免响应头没写字符集时中文按 ISO-8859-1 解码成乱码
        byte[] resultBytes = asrRestClient.get().uri(URI.create(transcriptionUrl)).retrieve().body(byte[].class);
        List <String> cues;
        try
        {
            cues = toCues(objectMapper.readTree(resultBytes));
        }
        catch (IOException e)
        {
            throw new IllegalStateException("解析识别结果失败, taskId=" + taskId, e);
        }
        if (cues.isEmpty())
        {
            log.info("识别结果里没有句子，不生成字幕, taskId={}", taskId);
            return;
        }

        StringBuilder srt = new StringBuilder();
        for (int i = 0 ; i < cues.size() ; i++)
        {
            srt.append(i + 1).append('\n').append(cues.get(i)).append("\n\n");
        }
        try
        {
            Files.writeString(subtitlePath, srt.toString(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("写入字幕文件失败, subtitlePath=" + subtitlePath, e);
        }
        log.info("字幕生成成功, taskId={}, lines={}, subtitlePath={}", taskId, cues.size(), subtitlePath);
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
        Map <String, Object> body = Map.of("model",
                                           asrProperties.getModel(),
                                           "input",
                                           Map.of("file_urls", List.of(fileUrl)),
                                           "parameters",
                                           Map.of("language_hints", List.of("zh", "en"),
                                                  // 时间戳校准：让每个字的时间和声音对得更齐
                                                  "timestamp_alignment_enabled", true));

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
     * 把识别结果切成一条条字幕<hr/>
     * <p>识别按说话停顿断句，一句可能有二三十个字。这里按每个字自己的时间把长句切短：
     * 攒到 {@link #LINE_MIN_CHARS} 个字以后遇到标点就断，最多不超过 {@link #LINE_MAX_CHARS} 个字。</p>
     *
     * @return 每条字幕是「时间轴 + 换行 + 文字」，还没有序号
     */
    private List <String> toCues(JsonNode result)
    {
        List <String> cues = new ArrayList <>();
        // 抽音频时转成了单声道，只会有一条 transcript
        for (JsonNode sentence : result.path("transcripts").path(0).path("sentences"))
        {
            JsonNode words = sentence.path("words");
            if (words.isEmpty())
            {
                addCue(cues,
                       sentence.path("begin_time").asLong(),
                       sentence.path("end_time").asLong(),
                       sentence.path("text").asText());
                continue;
            }

            StringBuilder line = new StringBuilder();
            long lineStart = 0;
            long lineEnd = 0;
            for (JsonNode word : words)
            {
                String punctuation = word.path("punctuation").asText();
                String text = word.path("text").asText() + punctuation;

                // 再加这个字就超长了，先把攒着的这行输出
                if (!line.isEmpty() && line.length() + text.length() > LINE_MAX_CHARS)
                {
                    addCue(cues, lineStart, lineEnd, line.toString());
                    line.setLength(0);
                }

                if (line.isEmpty())
                {
                    lineStart = word.path("begin_time").asLong();
                }
                line.append(text);
                lineEnd = word.path("end_time").asLong();

                // 这行已经不短了，又正好停在标点上，在这里断开读起来最自然
                if (StringUtils.hasText(punctuation) && line.length() >= LINE_MIN_CHARS)
                {
                    addCue(cues, lineStart, lineEnd, line.toString());
                    line.setLength(0);
                }
            }
            if (!line.isEmpty())
            {
                addCue(cues, lineStart, lineEnd, line.toString());
            }
        }
        return cues;
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
