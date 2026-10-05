# 与线上相同：官方镜像加 IK 中文分词插件，插件版本必须与 ES 版本严格一致
FROM docker.elastic.co/elasticsearch/elasticsearch:8.19.14

RUN ./bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/8.19.14
