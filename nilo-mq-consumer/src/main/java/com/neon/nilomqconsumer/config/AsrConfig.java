package com.neon.nilomqconsumer.config;

import com.neon.nilomqconsumer.config.properties.AsrProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AsrProperties.class)
public class AsrConfig
{
    /**
     * 语音识别专用 HTTP 客户端<hr/>
     * <p>用 JDK 自带的 HTTP 客户端，方便设置上传超时时间</p>
     */
    @Bean
    RestClient asrRestClient(RestClient.Builder builder)
    {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)) // 建立连接最多等10s
                                          .build();
        // 使用 JDK 实现的 HTTP 客户端
        // 它的读超时从发出请求算到收到响应头，上传音频的整个过程都算在里面
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMinutes(3)); // 完整请求的超时时间为3分钟
        // 挂一个打日志的异常处理，接口报错时把响应体带进异常信息，错误原因（key 不对、参数不对）都写在响应体里。
        return builder.requestFactory(requestFactory).defaultStatusHandler(HttpStatusCode::isError, (request, response) ->
        {
            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("语音识别相关接口调用失败, host=" + request.getURI()
                                                                                       .getHost() + ", path=" + request.getURI()
                                                                                                                       .getPath() + ", status=" + response.getStatusCode()
                                                                                                                                                          .value() + ", body=" + body);
        }).build();
    }
}
