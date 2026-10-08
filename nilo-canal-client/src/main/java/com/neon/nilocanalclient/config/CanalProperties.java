package com.neon.nilocanalclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "canal")
public class CanalProperties
{
    /**
     * Canal Server 主机
     */
    private String host;

    /**
     * Canal Server 端口
     */
    private int port;

    /**
     * destination，需与 Server 实例名一致
     */
    private String destination;

    private String username;

    private String password;

    /**
     * 订阅表达式，例如 nilo\\.video_info
     */
    private String subscribe;

    private int batchSize = 1000;

    /**
     * 无消息时休眠毫秒数
     */
    private long idleSleepMs = 1000L;

    /**
     * 同步 comment 微服务副本开关<hr/>
     * 是否把 video_info、user_info 的变更同步到评论库的副本表。没有评论服务的环境（如压测环境）要关掉，否则消息只会失败重试。
     */
    private boolean commentReplicaEnabled = true;
}
