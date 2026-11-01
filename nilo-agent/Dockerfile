# nilo-agent 镜像，在仓库根目录构建：docker build -f nilo-agent/Dockerfile nilo-agent
# 分两步：先用 uv 按 uv.lock 把依赖和项目装进虚拟环境，再只把虚拟环境复制进运行镜像，uv 和源码都不进最终镜像

FROM python:3.13-slim AS build
# uv 和本地开发用的版本一致：换了版本，uv 可能认为 uv.lock 需要更新，--locked 会直接失败
COPY --from=ghcr.io/astral-sh/uv:0.11.8 /uv /bin/uv
WORKDIR /app
# UV_COMPILE_BYTECODE：安装时就把代码编译成 .pyc，容器启动时不用再编译
# UV_PYTHON_DOWNLOADS=0：只用镜像自带的 Python，不另外下载
ENV UV_COMPILE_BYTECODE=1 UV_PYTHON_DOWNLOADS=0
# 依赖和代码分两层装：先只装依赖，pyproject.toml 和 uv.lock 没变时这一层可以复用缓存
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project
# 再装项目本身。--no-editable 把代码打包装进虚拟环境，运行时用不到 src 目录
COPY src ./src
RUN uv sync --locked --no-dev --no-editable

# 和上面用同一个 Python 镜像：虚拟环境里的 python 指向镜像自带的解释器
FROM python:3.13-slim
COPY --from=build /app/.venv /app/.venv
# 虚拟环境的 bin 加进 PATH，直接运行 pyproject.toml 里声明的 nilo-agent 命令
# PYTHONUNBUFFERED：输出不缓冲，docker logs 里能立刻看到日志
ENV PATH="/app/.venv/bin:$PATH" PYTHONUNBUFFERED=1
ENTRYPOINT ["nilo-agent"]
