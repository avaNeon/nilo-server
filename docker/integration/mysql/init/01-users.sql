-- 测试环境专用的账号，密码都是测试值
-- 业务库 nilo 由 MYSQL_DATABASE 创建，表结构见 02-nilo-schema.sql

-- Nacos 的配置持久化库（与线上一致），表结构由 compose 里的 nacos-schema-import 从 Nacos 镜像导入
CREATE DATABASE IF NOT EXISTS nacos DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'nacos'@'%' IDENTIFIED BY 'nilo-it-nacos';
GRANT ALL PRIVILEGES ON nacos.* TO 'nacos'@'%';

-- Canal 读取 binlog 所需的最小权限
CREATE USER IF NOT EXISTS 'canal'@'%' IDENTIFIED BY 'nilo-it-canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';

-- mysqld-exporter 采集指标所需的只读权限
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY 'nilo-it-exporter' WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';

FLUSH PRIVILEGES;
