package com.neon.niloweb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 项目配置
 */
@Getter
@Setter
@Configuration
public class WebConfig
{
    /**
     * 【必填】项目文件夹根目录
     */
    @Value("${project.folder}")
    private String rootFilePath;

    /**
     * 查询视频分页大小
     */
    @Value("${project.pageSize:20}")
    private int pageSize;

    /**
     * 查询评论分页大小
     */
    @Value("${project.commentPageSize:10}")
    private int commentPageSize;

    /**
     * 子评论分页大小（超出则由前端显示"查看更多回复"）
     */
    @Value("${project.childrenCommentPageSize:5}")
    private int childrenCommentPageSize;

    /**
     * 默认查询评论深度
     */
    @Value("${project.commentSelectDepth:3}")
    private int commentSelectDepth;
}
