#!/bin/sh
# 初始化测试环境的 Nacos：设置管理员密码、创建命名空间 perf、写入服务配置。
# 由 compose 里的 nacos-init 在 Nacos 健康检查通过后执行；重复执行也安全（配置会被覆盖为同样的内容）
set -eu

NACOS=http://nacos:8848/nacos
ADMIN_PASSWORD=nilo-it-nacos-admin
NAMESPACE=perf

login() {
  curl -s -X POST "$NACOS/v1/auth/login" --data-urlencode "username=nacos" --data-urlencode "password=$1" \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

# 1. 初始化管理员 nacos 的密码。Nacos 2.4 起首次开启鉴权需要设置；已经初始化过会返回错误，忽略即可
curl -s -X POST "$NACOS/v1/auth/users/admin" --data-urlencode "password=$ADMIN_PASSWORD" || true
echo

# 2. 登录。如果表结构自带默认账号 nacos/nacos，先用默认密码登录，再改成测试密码
TOKEN=$(login "$ADMIN_PASSWORD")
if [ -z "$TOKEN" ]; then
  TOKEN=$(login nacos)
  if [ -z "$TOKEN" ]; then
    echo "登录 Nacos 失败" >&2
    exit 1
  fi
  curl -sf -X PUT "$NACOS/v1/auth/users?accessToken=$TOKEN&username=nacos&newPassword=$ADMIN_PASSWORD"
  echo
  TOKEN=$(login "$ADMIN_PASSWORD")
fi

# 3. 创建命名空间（已存在时返回错误，忽略）
curl -s -X POST "$NACOS/v1/console/namespaces?accessToken=$TOKEN" \
  --data-urlencode "customNamespaceId=$NAMESPACE" \
  --data-urlencode "namespaceName=$NAMESPACE" \
  --data-urlencode "namespaceDesc=integration test" || true
echo

# 4. 发布配置
publish() {
  curl -sf -X POST "$NACOS/v1/cs/configs?accessToken=$TOKEN" \
    --data-urlencode "tenant=$NAMESPACE" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "dataId=$1" \
    --data-urlencode "type=$2" \
    --data-urlencode "content@/nacos/$1"
  echo " <- $1"
}

publish nilo-common.yaml yaml
publish nilo-sentinel-param-flow json
