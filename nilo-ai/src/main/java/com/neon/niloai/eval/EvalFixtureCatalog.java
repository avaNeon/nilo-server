package com.neon.niloai.eval;

import java.util.List;

/**
 * 评测假数据。exact 题：原词只放标签，标题故意不像；旁边再放同主题长简介当诱饵，向量容易找错。
 * semantic 题：简介用同义说法，标题不写原词，向量应该还能中。
 */
public final class EvalFixtureCatalog
{
    private EvalFixtureCatalog()
    {
    }

    public static List <EvalVideo> videos()
    {
        return List.of(
                // exact: JDK17 —— 原词只在标签
                video(990001L, "机房3排2号工位贴", "JDK17", "这台电脑别重装，重装了找值班。"),
                video(990002L,
                      "现代 Java 语言特性一次讲完",
                      "Java,LTS",
                      "从记录类型、密封类、模式匹配讲到文本块，重点围绕 Java 17 作为长期支持版本后团队该怎么选编译目标，以及和 21 的取舍。"),
                video(990003L,
                      "LTS 版本到底跟哪条线",
                      "Java",
                      "生产环境是跟 17 还是跟 21，发行节奏、安全更新窗口和第三方库兼容性都摊开讲，适合做技术选型会材料。"),
                video(990004L, "Java 8 迁到 11 的坑", "Java,JDK11", "模块化、JDK 11 客户端和过时 API，全程不讨论 17。"),

                // exact: 晴天 —— 原词只在标签
                video(990005L, "手机竖屏没对焦", "周杰伦,晴天", "朋友让我传上来，画质自行克服。"),
                video(990006L,
                      "华语情歌编年史",
                      "情歌",
                      "从周杰伦七里香、稻香、青花瓷一路数到后来的告白气球，把华语流行里最常被点的情歌串成课。"),
                video(990007L, "周杰伦《七里香》官方MV", "周杰伦,七里香,MV", "同歌手另一首，标题不含晴天。"),
                video(990008L, "林俊杰《江南》官方MV", "林俊杰,江南,MV", "华语男声代表作。"),
                video(990009L, "失恋疗伤歌单混剪", "情歌,失恋", "分手、错过、深夜独酌剪在一起，标题没有具体歌名。"),
                video(990010L, "G.E.M. 邓紫棋《光年之外》", "邓紫棋,光年之外", "电影主题曲官方版本。"),

                // exact: 王小波 —— 人名只在标签；诱饵讲黄金时代但不写人名
                video(990011L, "读书会签到表（第三周）", "王小波", "来的人在评论区报一下。"),
                video(990012L,
                      "九十年代小说改编影像课",
                      "文学",
                      "黄金时代、活着、许三观卖血记的影视改编对比，讨论小说语气怎么被镜头改掉。"),
                video(990013L, "余华《活着》影视剪辑", "余华,活着", "另一位作家，用来打散人名检索。"),
                video(990014L, "《黄金时代》文本细读", "黄金时代", "只谈小说结构，不下作者全名。"),

                // exact: MySQL 8.4
                video(990015L, "今晚变更单 #884", "MySQL8.4", "窗口只有半小时，按工单执行。"),
                video(990016L,
                      "InnoDB 索引与优化大全",
                      "MySQL",
                      "B+树、覆盖索引、函数索引和 JSON 文档怎么建索引，适合当手册，不钉某个小版本号。"),
                video(990017L, "MySQL 5.7 迁到 8.0", "MySQL,8.0", "认证插件和保留字，不是 8.4。"),

                // exact: ES 8.19
                video(990018L, "值班备忘（勿删）", "ES8.19", "集群控制台别乱点。"),
                video(990019L, "倒排索引从零讲", "ES,搜索", "term 怎么指向文档列表，和正排、向量检索的差别。"),
                video(990020L, "Elasticsearch 实战课", "ES", "mapping、分词、副本和单节点开发环境，不写具体小版本。"),

                // exact: Anti-Hero
                video(990021L, "通话录音 11 分 04 秒", "Anti-Hero", "英语歌名我不会拼，标签是听写的。"),
                video(990022L,
                      "Taylor Swift 时代巡回混剪",
                      "TaylorSwift",
                      "Love Story、Blank Space、Shake It Off 和 Folklore 段落串烧，没有单独切主打。"),
                video(990023L, "欧美流行精选", "流行", "电台风格混剪，不点名单曲。"),

                // exact: 天份 现场
                video(990024L, "副机位没对上焦", "薛之谦,天份,live", "只能当个声音备份。"),
                video(990025L, "薛之谦演唱会全程", "薛之谦", "演员、认真的雪、丑八怪一路唱下来，本场没有切那一首的特写。"),
                video(990026L, "薛之谦《演员》官方MV", "薛之谦,演员,MV", "工作室作品。"),

                // exact: Boot 3.3
                video(990027L, "依赖锁定文件说明", "Boot3.3", "版本钉死了，不要随手升级。"),
                video(990028L,
                      "Spring Boot 3 迁移完整教程",
                      "Spring,Boot3",
                      "Jakarta 命名空间、观察者、配置文件搬家，按大版本讲，不抠 3.3 补丁号。"),
                video(990029L, "Spring Bean 从创建到销毁", "Spring,IoC", "构造、注入、初始化回调和销毁钩子。"),

                // exact: Canal —— 诱饵简介里大讲 binlog 同步
                video(990030L, "工单 20240920", "Canal", "找 DBA 对一下位点。"),
                video(990031L,
                      "MySQL 变更如何进搜索引擎",
                      "MySQL,ES",
                      "从 binlog 解析、行镜像到增量写入 Elasticsearch，中间会提到 Canal 这类监听组件怎么接。"),

                video(990032L, "Redis 缓存击穿与雪崩", "Redis,缓存", "热点 key 失效打穿，以及大量 key 同时过期，顺带对比穿透。"),
                video(990033L, "什么是 RAG 检索增强生成", "RAG,LLM", "先搜再答，避免模型瞎编站内知识。"),
                video(990034L, "关键词打分和向量检索差在哪", "搜索", "专有名词对关键词友好，同义改写对向量友好。"),
                video(990035L, "夜雨声烦吉他指弹", "吉他,纯音乐", "无人声，适合当背景。"),
                video(990036L, "适合熬夜写代码的 Lo-fi 循环", "lo-fi,熬夜", "低保真节奏，没有人声打断思路。"),
                video(990037L, "JVM 停顿调优实战", "JVM,GC", "对比 CMS、G1 与 ZGC，根据停顿目标看垃圾回收器日志。"),
                video(990038L, "漫画图解内存回收", "Java,内存", "用大白话讲对象什么时候能被收回，不写调优。"),
                video(990039L, "凌晨两点的备播带", "江南", "电台录播切片，片头片尾都切掉了。"),
                video(990040L, "林俊杰演唱会合集", "林俊杰", "曹操、修炼爱情、小酒窝串烧，没有单独切那一首。"));
    }

    /**
     * exact：答案几乎只靠标签上的原词；semantic：靠简介里的同义说法。
     */
    public static List <EvalCase> cases()
    {
        return List.of(evalCase("JDK17", "exact", 990001L),
                       evalCase("周杰伦 晴天", "exact", 990005L),
                       evalCase("王小波", "exact", 990011L),
                       evalCase("MySQL 8.4", "exact", 990015L),
                       evalCase("Elasticsearch 8.19", "exact", 990018L),
                       evalCase("薛之谦 天份 现场", "exact", 990024L),
                       evalCase("Anti-Hero", "exact", 990021L),
                       evalCase("Spring Boot 3.3", "exact", 990027L),
                       evalCase("Canal binlog", "exact", 990030L),
                       evalCase("江南", "exact", 990039L),
                       evalCase("垃圾回收", "semantic", 990037L, 990038L),
                       evalCase("GC 调优", "semantic", 990037L),
                       evalCase("失恋很难过想听歌", "semantic", 990009L),
                       evalCase("适合熬夜听的背景音乐", "semantic", 990035L, 990036L),
                       evalCase("检索增强是什么", "semantic", 990033L),
                       evalCase("Bean 从创建到销毁", "semantic", 990029L),
                       evalCase("缓存被打穿了怎么办", "semantic", 990032L),
                       evalCase("倒排索引是什么", "semantic", 990019L),
                       evalCase("怎么把 MySQL 变更同步到搜索引擎", "semantic", 990031L),
                       evalCase("没有人声的吉他", "semantic", 990035L));
    }

    public static List <Long> videoIds()
    {
        return videos().stream().map(EvalVideo::videoId).toList();
    }

    private static EvalVideo video(long videoId, String videoName, String tags, String introduction)
    {
        return new EvalVideo(videoId, videoName, tags, introduction);
    }

    private static EvalCase evalCase(String question, String type, Long... expectedVideoIds)
    {
        return new EvalCase(question, type, List.of(expectedVideoIds));
    }

    public record EvalVideo(Long videoId, String videoName, String tags, String introduction)
    {
    }

    public record EvalCase(String question, String type, List <Long> expectedVideoIds)
    {
    }
}
