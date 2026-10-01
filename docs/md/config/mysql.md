# MySQL 配置

本文记录 MySQL 配置

## binlog 使用 ROW 格式

Canal 通过读取 MySQL 的 binlog 获取数据变更，再由 nilo-canal-client 同步到 Elasticsearch。因此 MySQL 必须开启 binlog，并使用
ROW 格式：

```ini
log-bin=mysql-bin
server_id=1
binlog-format=ROW
binlog_row_image=FULL
```

- ROW 格式记录每一行变更前后的具体值，Canal 才能得到变化的数据。STATEMENT 格式只记录 SQL 语句，无法使用。
- `binlog_row_image=FULL` 记录每一行的全部列，而不只是被修改的列。
- `server_id` 在同一套复制关系中必须唯一，不能与 Canal 的 `slaveId` 相同。

> 注：MySQL 8 默认即为 ROW 格式，配置文件中显式写出，是为了避免被误改。
