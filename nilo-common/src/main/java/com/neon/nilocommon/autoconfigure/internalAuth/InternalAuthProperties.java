package com.neon.nilocommon.autoconfigure.internalAuth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 集群内部接口鉴权配置<hr/>
 * <p>建议放到 Nacos 公共配置 nilo-common.yaml 中，所有服务共用同一 token</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nilo.internal")
public class InternalAuthProperties
{
    /**
     * 是否启用内部鉴权（入站校验 /inner/**，出站 Feign 自动带 token）
     */
    private boolean enabled = true;

    /**
     * 内部共享密钥，请使用足够长的随机串，不要提交到 git
     */
    private String token = "";
}
