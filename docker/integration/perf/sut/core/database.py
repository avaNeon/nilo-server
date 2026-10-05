"""数据库引擎：SQLAlchemy 的 Engine，相当于 Java 的 DataSource，负责管理到 MySQL 的连接"""
# 第三方库
from sqlalchemy import create_engine

# 本项目
from core.config import MYSQL_URL

# isolation_level='AUTOCOMMIT'：每条 SQL 自动提交，不开事务。
# 写入监控每 0.1 秒查一次播放量总和，必须每次都读到最新数据；如果开着事务，
# MySQL 默认的可重复读隔离级别会让同一个事务一直读到第一次查询时的快照，播放量看起来永远不变，监控会误判写入早就停了
engine = create_engine(MYSQL_URL, isolation_level='AUTOCOMMIT')
