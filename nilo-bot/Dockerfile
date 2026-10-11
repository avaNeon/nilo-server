# nilo-bot 镜像，在仓库根目录构建：docker build -f nilo-bot/Dockerfile nilo-bot
# 分两步：先用 Go 镜像编译出一个可执行文件，再只把这个文件放进空镜像，最终镜像只有几 MB

FROM golang:1.23-alpine AS build
WORKDIR /src
# 代码分在 api、model、service、util 几个子目录里，整个目录一起复制
COPY . ./
# CGO_ENABLED=0：不依赖任何系统库，空镜像里也能直接运行
# -trimpath、-ldflags="-s -w"：去掉本机路径和调试信息，文件更小
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /nilo-bot .

# scratch 是完全空的镜像，没有 shell 和系统工具，只放上面编译出的文件
FROM scratch
COPY --from=build /nilo-bot /nilo-bot
ENTRYPOINT ["/nilo-bot"]
