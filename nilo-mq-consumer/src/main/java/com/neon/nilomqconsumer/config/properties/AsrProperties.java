package com.neon.nilomqconsumer.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音识别（阿里云百炼录音文件识别）配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "asr")
public class AsrProperties
{
    private String apiKey;

    /**
     * 百炼接口地址，不带结尾斜杠，比如 https://xxx.cn-beijing.maas.aliyuncs.com
     */
    private String baseUrl;

    /**
     * 识别模型，比如 paraformer-v2
     */
    private String model;
}
