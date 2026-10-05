# 与线上相同的 Sentinel Dashboard 控制台镜像（官方没有镜像，用发布的 jar 构建）。
# 线上把 jar 放在构建目录里；这里构建时从官方 GitHub Releases 下载同一版本
FROM eclipse-temurin:8-jre-alpine

RUN addgroup -S sentinel && adduser -S sentinel -G sentinel

WORKDIR /app

ADD --chown=sentinel:sentinel https://github.com/alibaba/Sentinel/releases/download/1.8.8/sentinel-dashboard-1.8.8.jar /app/sentinel-dashboard.jar

USER sentinel

ENV JAVA_OPTS="-Xms256m -Xmx512m"
# 账号密码由 compose 的环境变量覆盖
ENV SENTINEL_DASHBOARD_USERNAME=sentinel
ENV SENTINEL_DASHBOARD_PASSWORD=sentinel

EXPOSE 8080
EXPOSE 8719

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=8080 -Dcsp.sentinel.dashboard.server=localhost:8080 -Dproject.name=sentinel-dashboard -Dsentinel.dashboard.auth.username=$SENTINEL_DASHBOARD_USERNAME -Dsentinel.dashboard.auth.password=$SENTINEL_DASHBOARD_PASSWORD -jar /app/sentinel-dashboard.jar"]
