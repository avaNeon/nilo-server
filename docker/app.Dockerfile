# 通用运行时镜像模板：适用于所有可独立运行的 nilo-* 服务
# （nilo-common 是共享库，没有 spring-boot-maven-plugin 的 repackage，不产出可运行 jar，不用这个模板）
#
# 用法（构建上下文必须是仓库根目录，因为要读取 ${MODULE}/target/*.jar）：
#   mvn -B -DskipTests package
#   docker build -f docker/app.Dockerfile --build-arg MODULE=nilo-web -t nilo-web:local .

FROM eclipse-temurin:17-jre-alpine

ARG MODULE

WORKDIR /app
COPY ${MODULE}/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
