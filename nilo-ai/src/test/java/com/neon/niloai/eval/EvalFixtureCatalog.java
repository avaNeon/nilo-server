package com.neon.niloai.eval;

import com.neon.niloai.service.VideoVectorIndexService;
import com.neon.nilocommon.entity.dto.VideoEmbedSourceDTO;
import org.springframework.ai.document.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索评测样例<hr/>
 *
 * <p>exact 题：标准答案是用户点名要的那一个好视频，诱饵是同系列的兄弟（同一产品线的其它版本、同一歌手的其它歌），
 * 语义上几乎无法区分，唯一的区别就是那个确切的词。用来检验向量是否分得清 8.4 和 8.0、晴天和七里香。
 *
 * <p>semantic 题：用户用同义说法提问，标题里没有原词。作为回归测试，确认改动没把语义能力弄坏。
 *
 * <p>标准答案必须无争议：任何人看了都会同意用户要的就是它。
 */
final class EvalFixtureCatalog
{
    private EvalFixtureCatalog()
    {
    }

    /**
     * 约 100 条语料，文本和 metadata 与正式灌入完全一致
     */
    static List <Document> videos()
    {
        return List.of(
                // ==================== MySQL 版本族（答案 990101） ====================
                video(990101L,
                      "MySQL 8.4 新特性详解",
                      "MySQL,8.4",
                      "逐条过一遍这一版的改动：默认认证插件切换、组复制参数调整、若干旧语法移除。"),
                video(990102L, "MySQL 8.0 新特性详解", "MySQL,8.0", "窗口函数、CTE、原子 DDL 和 JSON 增强，逐条讲这一版带来的变化。"),
                video(990103L,
                      "MySQL 5.7 升级到 8.0 实战",
                      "MySQL,升级",
                      "认证插件、保留字冲突和字符集变更，按真实工单走一遍升级流程。"),
                video(990104L, "MySQL 主从复制配置", "MySQL,复制", "从零搭一主两从，讲清位点、半同步和延迟排查。"),
                video(990105L, "InnoDB 索引优化大全", "MySQL,索引", "B+树结构、覆盖索引、函数索引和联合索引的最左前缀。"),
                video(990106L, "MySQL 慢查询排查", "MySQL,性能", "开慢日志、看执行计划、定位没走索引的语句。"),

                // ==================== JDK 版本族（答案 990111） ====================
                video(990111L,
                      "JDK 17 新增了什么",
                      "Java,JDK17",
                      "密封类、模式匹配预览、增强的伪随机数生成器，以及这一版移除了哪些东西。"),
                video(990112L, "JDK 21 新增了什么", "Java,JDK21", "虚拟线程、结构化并发、记录模式，逐条过这一版的新能力。"),
                video(990113L, "JDK 11 新增了什么", "Java,JDK11", "HTTP Client 正式版、var 用于 lambda 参数、单文件源码直接运行。"),
                video(990114L, "Java 8 Lambda 与 Stream", "Java,Java8", "函数式接口、流水线操作和收集器，打基础用。"),
                video(990115L, "Java 泛型与类型擦除", "Java,泛型", "通配符、边界和擦除带来的坑。"),
                video(990116L, "Java 并发编程入门", "Java,并发", "线程、锁、volatile 和内存可见性的基本盘。"),

                // ==================== Spring Boot 版本族（答案 990121） ====================
                video(990121L,
                      "Spring Boot 3.3 升级指南",
                      "Spring,Boot3.3",
                      "这一版的配置项变更、依赖对齐清单和需要注意的破坏性改动。"),
                video(990122L,
                      "Spring Boot 3.2 升级指南",
                      "Spring,Boot3.2",
                      "虚拟线程支持、RestClient 引入，以及从上一版升上来要改什么。"),
                video(990123L, "Spring Boot 2.7 升级指南", "Spring,Boot2.7", "自动配置注册方式变更，为跨大版本升级做准备。"),
                video(990124L, "Spring Boot 自动配置原理", "Spring,原理", "条件注解、starter 机制和自动配置类的加载顺序。"),
                video(990125L, "Spring Bean 生命周期", "Spring,IoC", "构造、依赖注入、初始化回调、销毁钩子，以及各扩展点的触发时机。"),
                video(990126L, "Spring Cloud 微服务入门", "Spring,微服务", "注册中心、配置中心、网关和服务间调用。"),

                // ==================== Elasticsearch 版本族（答案 990131） ====================
                video(990131L,
                      "Elasticsearch 8.19 向量检索实战",
                      "ES,8.19",
                      "这一版的 dense_vector 字段配置、kNN 查询写法和索引参数调整。"),
                video(990132L, "Elasticsearch 8.0 升级要点", "ES,8.0", "安全默认开启、REST 客户端替换和映射变更。"),
                video(990133L, "Elasticsearch 7.17 集群运维", "ES,7.17", "滚动重启、分片分配和这一版的监控指标。"),
                video(990134L, "倒排索引原理详解", "ES,搜索", "词项怎么指向文档列表，和正排存储的差别，以及打分是怎么算出来的。"),
                video(990135L, "ES 分词器与 IK 配置", "ES,分词", "标准分词、IK 细粒度与智能切分，以及自定义词典。"),

                // ==================== Redis 版本族（答案 990141） ====================
                video(990141L, "Redis 7.2 有什么变化", "Redis,7.2", "这一版的函数增强、客户端信息和若干命令行为调整。"),
                video(990142L, "Redis 6.2 有什么变化", "Redis,6.2", "多线程 IO、ACL 细化和过期策略改动。"),
                video(990143L,
                      "Redis 缓存击穿与雪崩",
                      "Redis,缓存",
                      "热点 key 失效瞬间大量请求压到数据库，以及大批 key 同时过期的连锁反应，顺带对比穿透。"),
                video(990144L, "Redis 持久化 RDB 与 AOF", "Redis,持久化", "两种落盘方式的取舍和混合持久化。"),

                // ==================== Kubernetes 版本族（答案 990151） ====================
                video(990151L, "Kubernetes 1.30 更新了什么", "K8s,1.30", "这一版稳定的特性门控、废弃的 API 组和调度器改动。"),
                video(990152L, "Kubernetes 1.28 更新了什么", "K8s,1.28", "这一版的 sidecar 容器、作业管理改进和 API 变更。"),
                video(990153L, "Docker 网络模式详解", "Docker,网络", "bridge、host、none 与自定义网络的区别。"),
                video(990154L, "K8s Service 与 Ingress", "K8s,网络", "四层与七层暴露方式，以及流量怎么进到 Pod。"),
                video(990155L, "容器编排入门", "K8s,容器", "Pod、Deployment、ReplicaSet 的关系。"),

                // ==================== 前端框架版本族（答案 990161） ====================
                video(990161L, "Vue 3.4 有什么新东西", "Vue,3.4", "这一版的编译器重写、defineModel 转正和响应式性能改进。"),
                video(990162L, "Vue 3.2 有什么新东西", "Vue,3.2", "这一版的 script setup 转正、CSS 变量注入和响应式 API 调整。"),
                video(990163L, "Vue 2 迁移到 Vue 3", "Vue,迁移", "组合式 API、破坏性变更清单和兼容构建。"),
                video(990164L, "React 18 并发特性", "React,18", "并发渲染、自动批处理和 Suspense 改进。"),
                video(990165L, "前端构建工具对比", "前端,构建", "Webpack、Vite 与 Rollup 的定位差别。"),

                // ==================== JVM 族（semantic 答案 990171 / 990172） ====================
                video(990171L, "GC 停顿调优实战", "JVM,GC", "对比 CMS、G1 与 ZGC 三种收集器，按停顿时间目标读日志、调参数。"),
                video(990172L, "对象什么时候会被清理", "Java,内存", "用大白话讲对象的可达性分析、引用类型和何时被清出堆。"),
                video(990173L, "JVM 内存模型详解", "JVM,内存", "堆、栈、方法区和直接内存的划分。"),
                video(990174L, "类加载机制与双亲委派", "JVM,类加载", "加载、验证、准备、解析、初始化五个阶段。"),
                video(990175L, "JVM 参数调优清单", "JVM,调优", "常用启动参数与各自的适用场景。"),

                // ==================== 消息队列族（答案 990181） ====================
                video(990181L, "RocketMQ 5.0 新特性", "RocketMQ,5.0", "这一版的存算分离架构、轻量客户端和 Proxy 模式。"),
                video(990182L, "RocketMQ 4.9 运维手册", "RocketMQ,4.9", "这一版的集群部署、刷盘策略和主从切换。"),
                video(990183L, "Kafka 分区与副本", "Kafka,分区", "分区分配、ISR 机制和 leader 选举。"),
                video(990184L, "消息幂等性设计", "MQ,幂等", "重复投递怎么防，去重表与业务唯一键。"),

                // ==================== 周杰伦族（答案 990201） ====================
                video(990201L, "周杰伦 - 晴天", "周杰伦,晴天", "2003 年专辑收录曲，官方影像。"),
                video(990202L, "周杰伦 - 七里香", "周杰伦,七里香", "2004 年同名专辑主打，官方影像。"),
                video(990203L, "周杰伦 - 稻香", "周杰伦,稻香", "2008 年专辑收录曲，官方影像。"),
                video(990204L, "周杰伦 - 青花瓷", "周杰伦,青花瓷", "2007 年专辑主打，官方影像。"),
                video(990205L, "周杰伦 - 告白气球", "周杰伦,告白气球", "2016 年专辑收录曲，官方影像。"),
                video(990206L, "周杰伦 - 夜曲", "周杰伦,夜曲", "2005 年专辑主打，官方影像。"),
                video(990207L, "周杰伦演唱会现场混剪", "周杰伦,演唱会", "历年巡演片段串烧，未收录完整单曲。"),

                // ==================== 林俊杰族（答案 990211） ====================
                video(990211L, "林俊杰 - 江南", "林俊杰,江南", "2004 年专辑收录曲，官方影像。"),
                video(990212L, "林俊杰 - 曹操", "林俊杰,曹操", "2006 年同名专辑主打，官方影像。"),
                video(990213L, "林俊杰 - 修炼爱情", "林俊杰,修炼爱情", "2013 年专辑主打，官方影像。"),
                video(990214L, "林俊杰 - 小酒窝", "林俊杰,小酒窝", "2008 年合唱曲，官方影像。"),
                video(990215L, "林俊杰 - 可惜没如果", "林俊杰,可惜没如果", "2014 年专辑主打，官方影像。"),

                // ==================== Taylor Swift 族（答案 990221） ====================
                video(990221L, "Taylor Swift - Anti-Hero", "TaylorSwift,Anti-Hero", "2022 年专辑主打，官方影像。"),
                video(990222L, "Taylor Swift - Blank Space", "TaylorSwift,BlankSpace", "2014 年专辑主打，官方影像。"),
                video(990223L, "Taylor Swift - Love Story", "TaylorSwift,LoveStory", "2008 年专辑主打，官方影像。"),
                video(990224L, "Taylor Swift - Shake It Off", "TaylorSwift,ShakeItOff", "2014 年专辑首支单曲，官方影像。"),
                video(990225L, "Taylor Swift - Cruel Summer", "TaylorSwift,CruelSummer", "2019 年专辑收录曲，官方影像。"),

                // ==================== 薛之谦族（答案 990231） ====================
                video(990231L, "薛之谦 - 天份 现场版", "薛之谦,天份,live", "巡演现场收录，乐队编制。"),
                video(990232L, "薛之谦 - 演员 现场版", "薛之谦,演员,live", "巡演现场收录，乐队编制。"),
                video(990233L, "薛之谦 - 丑八怪 现场版", "薛之谦,丑八怪,live", "巡演现场收录，乐队编制。"),
                video(990234L, "薛之谦 - 认真的雪 现场版", "薛之谦,认真的雪,live", "巡演现场收录，乐队编制。"),
                video(990235L, "薛之谦 - 绅士 现场版", "薛之谦,绅士,live", "巡演现场收录，乐队编制。"),

                // ==================== 作家族（答案 990241） ====================
                video(990241L, "王小波《黄金时代》解读", "王小波,黄金时代", "叙事语气、时间结构和荒诞感的来源。"),
                video(990242L, "余华《活着》解读", "余华,活着", "苦难叙事与人物命运的安排。"),
                video(990243L, "莫言《红高粱》解读", "莫言,红高粱", "叙事视角与民间语汇的运用。"),
                video(990244L, "钱钟书《围城》解读", "钱钟书,围城", "讽刺手法与知识分子群像。"),
                video(990245L, "当代文学阅读入门", "文学,阅读", "怎么从结构、语气和视角入手读一本小说。"),

                // ==================== 邓紫棋族 ====================
                video(990251L, "邓紫棋 - 光年之外", "邓紫棋,光年之外", "2017 年电影主题曲，官方影像。"),
                video(990252L, "邓紫棋 - 泡沫", "邓紫棋,泡沫", "2012 年专辑收录曲，官方影像。"),
                video(990253L, "邓紫棋 - 喜欢你", "邓紫棋,喜欢你", "翻唱版本，官方影像。"),
                video(990254L, "邓紫棋演唱会现场混剪", "邓紫棋,演唱会", "历年巡演片段串烧。"),

                // ==================== 五月天族 ====================
                video(990261L, "五月天 - 突然好想你", "五月天,突然好想你", "2008 年专辑收录曲，官方影像。"),
                video(990262L, "五月天 - 倔强", "五月天,倔强", "2004 年专辑主打，官方影像。"),
                video(990263L, "五月天 - 温柔", "五月天,温柔", "2001 年专辑收录曲，官方影像。"),
                video(990264L, "五月天 - 知足", "五月天,知足", "2005 年专辑收录曲，官方影像。"),

                // ==================== 氛围音乐族（semantic 答案 990311 / 990312 / 990313 / 990314） ====================
                video(990311L, "吉他指弹：夜雨声烦", "吉他,纯音乐", "整轨只有木吉他演奏，从头到尾没有唱词。"),
                video(990312L,
                      "Lo-fi 循环：深夜书房",
                      "lo-fi,循环",
                      "低保真鼓组配轻钢琴，很多人拿它当写程序时的背景垫底，不会抢注意力。"),
                video(990313L, "钢琴纯音乐合辑", "钢琴,纯音乐", "三小时演奏，无唱词，适合长时间播放。"),
                video(990314L,
                      "失恋疗伤歌单混剪",
                      "情歌,失恋",
                      "把错过、告别、深夜独酌的那些曲子剪在一起，适合刚结束一段关系的时候听。"),
                video(990315L, "华语情歌编年史", "情歌,华语", "从千禧年前后一路梳理到近年，讲这些歌为什么会流行。"),

                // ==================== AI / 搜索族（semantic 答案 990321 / 990322） ====================
                video(990321L,
                      "RAG 检索增强生成入门",
                      "RAG,LLM",
                      "先从知识库里查出依据再让模型作答，这样它就不会凭空捏造站内不存在的内容。"),
                video(990322L,
                      "binlog 到 Elasticsearch 的增量同步",
                      "MySQL,ES,同步",
                      "监听变更日志、解析行镜像，让底层表一有改动，搜索侧的索引就跟着更新。"),
                video(990323L, "向量检索与关键词检索的差别", "搜索,向量", "一个比意思像不像，一个比字面有没有，各自擅长什么。"),
                video(990324L, "Embedding 与文本切块", "Embedding,切块", "文字怎么变成一串数，长文为什么要切开分别处理。"),

                // ==================== Java 基础 / 算法族（semantic 答案 990334） ====================
                video(990331L, "HashMap 源码剖析", "Java,集合", "数组加链表转红黑树，扩容与哈希扰动。"),
                video(990332L, "ConcurrentHashMap 原理", "Java,并发", "分段思路的演进与 CAS 加 synchronized 的组合。"),
                video(990333L, "synchronized 与 Lock 对比", "Java,锁", "偏向、轻量、重量级的升级路径与可重入锁的差别。"),
                video(990334L,
                      "线程池参数详解",
                      "Java,线程池",
                      "核心数、最大数、队列容量和拒绝策略怎么搭，任务堆积时该往哪个方向调。"),
                video(990335L, "动态规划入门", "算法,DP", "状态定义、转移方程和边界处理。"),
                video(990336L, "二叉树遍历全解", "算法,树", "前中后序与层序，递归和迭代两种写法。"),
                video(990337L, "红黑树图解", "算法,树", "五条性质、旋转与变色。"),
                video(990338L, "一致性哈希原理", "算法,分布式", "哈希环、虚拟节点与数据迁移量。"),

                // ==================== 网络族（semantic 答案 990341 / 990345） ====================
                video(990341L,
                      "TCP 三次握手与四次挥手",
                      "网络,TCP",
                      "两端从零开始协商序号、确认彼此收发能力，直到通道可用的全过程。"),
                video(990342L, "HTTP/2 与 HTTP/3", "网络,HTTP", "多路复用、头部压缩和底层协议的更换。"),
                video(990343L, "HTTPS 握手过程", "网络,HTTPS", "证书校验、密钥协商与对称加密切换。"),
                video(990344L, "DNS 解析流程", "网络,DNS", "递归与迭代查询，各级缓存的作用。"),
                video(990345L,
                      "负载均衡策略对比",
                      "网络,负载均衡",
                      "轮询、加权、最少连接和一致性哈希，把请求摊到多台机器上避免单点被压垮。"));
    }

    /**
     * 问题 → 期望命中的 videoId。查询里带一个确切的词，语料里有一群语义几乎相同的兄弟
     */
    static Map <String, List <Long>> exactCases()
    {
        Map <String, List <Long>> cases = new LinkedHashMap <>();
        cases.put("MySQL 8.4 有哪些新特性", List.of(990101L));
        cases.put("JDK 17 新增了什么", List.of(990111L));
        cases.put("Spring Boot 3.3 怎么升级", List.of(990121L));
        cases.put("Elasticsearch 8.19 的向量检索怎么用", List.of(990131L));
        cases.put("Redis 7.2 有什么变化", List.of(990141L));
        cases.put("Kubernetes 1.30 更新了什么", List.of(990151L));
        cases.put("Vue 3.4 有什么新东西", List.of(990161L));
        cases.put("RocketMQ 5.0 新特性", List.of(990181L));
        cases.put("周杰伦的晴天", List.of(990201L));
        cases.put("林俊杰的江南", List.of(990211L));
        cases.put("Taylor Swift 的 Anti-Hero", List.of(990221L));
        cases.put("薛之谦的天份现场", List.of(990231L));
        cases.put("王小波写的黄金时代", List.of(990241L));
        return cases;
    }

    /**
     * 问题 → 期望命中的 videoId。同义说法，标题里没有原词
     */
    static Map <String, List <Long>> semanticCases()
    {
        Map <String, List <Long>> cases = new LinkedHashMap <>();
        cases.put("垃圾回收是怎么回事", List.of(990171L, 990172L));
        cases.put("缓存被打穿了怎么办", List.of(990143L));
        cases.put("Bean 从创建到销毁经历了什么", List.of(990125L));
        cases.put("搜索引擎是怎么找到文档的", List.of(990134L));
        cases.put("怎么让大模型别瞎编", List.of(990321L));
        cases.put("数据库改了怎么让搜索也跟着变", List.of(990322L));
        cases.put("写代码的时候适合放什么背景音乐", List.of(990312L));
        cases.put("想听没有人声的演奏", List.of(990311L, 990313L));
        cases.put("刚分手想听点歌", List.of(990314L));
        cases.put("并发量上来了线程该怎么管", List.of(990334L));
        cases.put("两台机器建立连接的过程", List.of(990341L));
        cases.put("请求太多怎么摊到多台机器上", List.of(990345L));
        return cases;
    }

    private static Document video(long videoId, String videoName, String tags, String introduction)
    {
        String text = VideoVectorIndexService.buildEmbedText(new VideoEmbedSourceDTO(videoId, videoName, tags, introduction));
        return new Document(String.valueOf(videoId),
                            text,
                            Map.of(VideoVectorIndexService.META_VIDEO_ID,
                                   videoId,
                                   VideoVectorIndexService.META_VIDEO_NAME,
                                   videoName));
    }
}
