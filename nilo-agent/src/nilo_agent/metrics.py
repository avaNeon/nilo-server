from prometheus_client import Counter, Histogram

ASKS = Counter("nilo_agent_asks", "答完的提问次数", ["intent"])
"""
答完的提问次数，按意图分
"""

ASK_FAILURES = Counter("nilo_agent_ask_failures", "没答完的提问次数", ["kind"])
"""
没答完的提问次数。kind=business 是未登录、额度用完、会话过期这类请求本身的问题；kind=error 是服务端出错，会退额度
"""

ASK_SECONDS = Histogram("nilo_agent_ask_duration_seconds", "一次提问从开始到答完的秒数", ["intent"],
                        buckets=(1, 2, 5, 10, 20, 30, 60, 90))
"""
一次提问的耗时，按意图分。前端 90 秒超时，所以桶最大到 90
"""

TOOL_CALLS = Counter("nilo_agent_tool_calls", "Agent 调用工具的次数", ["tool"])
"""
Agent 调用各个工具的次数
"""
