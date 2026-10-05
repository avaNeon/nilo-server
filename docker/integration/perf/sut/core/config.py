"""连接信息与常量。

MySQL、Redis、ES 只监听被测机的本机回环地址，本目录的三个入口脚本都在被测机本机运行。
账号密码都是测试环境专用的值，与 docker/integration 里的配置一致，不含任何线上密钥。
"""
# 标准库
from base64 import b64encode

# MySQL 连接地址，格式：mysql+<驱动>://用户名:密码@主机:端口/库名，驱动用 PyMySQL
MYSQL_URL = 'mysql+pymysql://root:nilo-it-root@127.0.0.1:3306/nilo?charset=utf8mb4'
# decode_responses=True：Redis 返回的字节自动转成字符串
REDIS = dict(host='127.0.0.1', port=6379, password='nilo-it-redis', decode_responses=True)

ES_URL = 'http://127.0.0.1:9200'
# ES 使用 HTTP Basic 认证：请求头为 "Authorization: Basic <base64(用户名:密码)>"
ES_AUTH = 'Basic ' + b64encode(b'elastic:nilo-it-elastic').decode()
ES_INDEX = 'video_info_doc'

# 当日播放量哈希的 key 前缀，完整 key 是 nilo:{play-count}:daily:<UTC 日期>，与服务端代码一致
DAILY_PREFIX = 'nilo:{play-count}:daily:'
# 种子视频的起始 ID，与 load/load.js、工作流里的 VIDEO_ID_BASE 一致。数字里的下划线只是分隔符
VIDEO_ID_BASE = 900_000_000_000
