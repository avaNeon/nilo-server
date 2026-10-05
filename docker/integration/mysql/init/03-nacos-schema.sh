# 导入 Nacos 的表结构到 nacos 库。表结构取自 Nacos 2.5.1 安装包的 conf/mysql-schema.sql，与 Nacos 镜像版本一致
# 本文件由 MySQL 镜像的初始化流程 source 执行，docker_process_sql 是镜像入口脚本提供的函数
docker_process_sql --database=nacos < /nacos-schema.sql
