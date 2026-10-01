# 通用运行时镜像模板：适用于所有可独立运行的 nilo-* 服务
# （nilo-common 是共享库，没有 spring-boot-maven-plugin 的 repackage，不产出可运行 jar，不用这个模板）
#
# 用法（构建上下文必须是仓库根目录，因为要读取 ${MODULE}/target/*.jar）：
#   mvn -B -DskipTests package
#   docker build -f docker/app.Dockerfile --build-arg MODULE=nilo-web -t nilo-web:local .

FROM eclipse-temurin:17-jre-alpine

# 转码、字幕识别、图片缩略图都是通过命令行调用 ffmpeg / ffprobe（见 nilo-common 的 FfmpegUtil），必须装在镜像里。
# FfmpegUtil 在共享库里，任何服务都可能调用，所以所有服务的镜像都带上。
# 这一层放在 ARG MODULE 之前，七个镜像共用同一层，服务器只会拉取、保存一份。
# 顺带在构建时自检：缺 ffprobe 或没有 libx264 编码器就让构建失败。
RUN apk add --no-cache ffmpeg \
    && ffmpeg -version > /dev/null \
    && ffprobe -version > /dev/null \
    && ffmpeg -hide_banner -encoders | grep -q libx264

ARG MODULE

WORKDIR /app
COPY ${MODULE}/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
