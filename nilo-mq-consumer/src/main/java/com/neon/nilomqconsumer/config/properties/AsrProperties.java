package com.neon.nilomqconsumer.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 语音识别配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "asr")
public class AsrProperties
{
    private String apiKey;

    /**
     * 接口地址，不带结尾斜杠，比如 https://api.assemblyai.com
     */
    private String baseUrl;

    /**
     * 识别模型，按优先顺序排。识别出的语种前面的模型不支持时，自动换后面的
     */
    private List <String> speechModels;
}
