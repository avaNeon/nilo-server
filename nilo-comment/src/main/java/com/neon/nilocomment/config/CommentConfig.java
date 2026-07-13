package com.neon.nilocomment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 项目配置
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "project")
public class CommentConfig
{
    /**
     * 默认查询评论深度
     */
    private int commentSelectDepth = 3;

    /**
     * 子评论分页大小（超出则由前端显示"查看更多回复"）
     */
    private int childrenCommentPageSize = 5;

    /**
     * 查询评论分页大小
     */
    private int commentPageSize = 10;
}
