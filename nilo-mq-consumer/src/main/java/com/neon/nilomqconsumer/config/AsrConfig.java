package com.neon.nilomqconsumer.config;

import com.neon.nilomqconsumer.config.properties.AsrProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AsrProperties.class)
public class AsrConfig
{
    /**
     * 调语音识别用的 HTTP 客户端<hr/>
     * <p>不设默认的 Authorization 头：同一个客户端还要往 OSS 传文件、下载识别结果，那两处不能带百炼的 key。</p>
     * <p>接口报错时把响应体带进异常信息，百炼的错误原因（key 不对、参数不对）都写在响应体里。</p>
     * <p>外面包一层 Buffering：Spring 发 multipart 默认边写边发、不带 Content-Length（分块传输），
     * 往 OSS 表单上传不一定认这种方式。先整体攒到内存再发就会带上长度，音频一小时也就二十来 MB。</p>
     */
    @Bean
    RestClient asrRestClient(RestClient.Builder builder)
    {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS.withConnectTimeout(Duration.ofSeconds(10))
                                                                                             .withReadTimeout(Duration.ofSeconds(60));
        return builder.requestFactory(new BufferingClientHttpRequestFactory(ClientHttpRequestFactories.get(settings)))
                      .defaultStatusHandler(HttpStatusCode::isError, (request, response) ->
                      {
                          String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                          throw new IllegalStateException("语音识别相关接口调用失败, host=" + request.getURI().getHost()
                                                          + ", path=" + request.getURI().getPath()
                                                          + ", status=" + response.getStatusCode().value()
                                                          + ", body=" + body);
                      })
                      .build();
    }
}
