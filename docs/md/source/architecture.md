# 整体架构

本文说明各模块的职责。

- nilo-common：保存公共Java类、提供可选自动装配Bean、传递公共依赖、保存常量；非启动类，仅作为依赖使用
- nilo-web：主类，提供绝大多数业务接口
- nilo-ai：提供AI回答、MCP服务器、Agent调用功能
- nilo-mq-consumer：监听RabbitMQ的消费者，处理所有消费者逻辑
- nilo-admin：提供后台管理接口
- nilo-storage：存储微服务，提供与MinIO交互的存储功能相关的接口，接口全部用于内微服务调用
- nilo-comment：评论微服务，只负责评论相关逻辑；评论业务拆出，评论相关表分库，保证评论业务异常不影响核心功能，降低故障打击面。添加未迁移的关联表的冗余复制，利用分布式事务更改保证强一致
- nilo-canal-client：基于canal读取mysql的binlog，从而自动同步写入Elasticsearch，这里保留了从binlog到es的转化逻辑
