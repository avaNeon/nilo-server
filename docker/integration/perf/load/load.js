// 播放统计接口压测（k6），在压测机上运行，通过 Tailscale 隧道访问被测机。
//
// k6 按 options 里的设定，每秒调用 RATE 次下面的 default 函数（每次就是一个请求），
// 全部结束后调用 handleSummary 输出结果。
//
// 环境变量：
//   TARGET         被测机的接口地址（由 nginx 接住再转给 nilo-web），如 http://100.x.y.z:7071
//   RATE           每秒请求数（固定到达率：不受响应快慢影响，按时发出）
//   DURATION       持续时间，如 3m
//   VIDEO_COUNT    种子视频数量，视频ID从 VIDEO_ID_BASE 开始连续编号
//   VIDEO_ID_BASE  种子视频的起始ID，与 sut/core/config.py 一致
//   DISTRIBUTION   uniform：均匀分布；hot：80% 的请求集中在 1% 的视频上
//   SUMMARY_FILE   结果文件名
//
// 每个请求带随机的 sessionId 和 X-Real-IP，模拟不同的用户，使请求都走放行路径（会话、IP 两个维度的限流都不会触发）。
import http from 'k6/http';
import { Counter, Gauge, Trend } from 'k6/metrics';

const TARGET = __ENV.TARGET;
const RATE = Number(__ENV.RATE || 200);
const DURATION = __ENV.DURATION || '3m';
const VIDEO_COUNT = Number(__ENV.VIDEO_COUNT || 100000);
const VIDEO_ID_BASE = Number(__ENV.VIDEO_ID_BASE || 900000000000);
const DISTRIBUTION = __ENV.DISTRIBUTION || 'uniform';
const SUMMARY_FILE = __ENV.SUMMARY_FILE || 'k6-summary.json';

// 放行、被限流、失败的请求数，对账以"放行"为准
const accepted = new Counter('play_accepted');
const rejected = new Counter('play_rejected');
const failed = new Counter('play_failed');
// 失败按原因再分别计数（合计等于 play_failed），不用翻 nginx 日志就能知道失败是怎么来的
const failedBy = {
  // nginx 返回 502：同时转发中的请求已达上限（max_conns），或者连不上 nilo-web
  status502: new Counter('play_failed_502'),
  status5xx: new Counter('play_failed_5xx_other'),  // 其他 5xx，如 nilo-web 自己报错的 500、nginx 等待超时的 504
  status4xx: new Counter('play_failed_4xx'),
  network: new Counter('play_failed_network'),      // 没收到响应：连接失败或超时，k6 记为状态码 0
  other: new Counter('play_failed_other'),          // 状态码 200 但响应内容不是预期的，或者少见的状态码（如重定向）
};
// 放行请求的响应时间。k6 自带的 http_req_duration 包含失败的请求：失败很多时（如 nginx 立即返回的 502），
// 整体的响应时间会被拉偏，看不出放行的请求实际花了多久。第二个参数 true 表示这是时间，单位毫秒
const acceptedDuration = new Trend('play_accepted_duration', true);
// 每收到一个响应就记一次当前时间。Gauge 会保留最大值，即最后一个响应到达的时刻，作为"压测结束时刻"
const lastResponse = new Gauge('last_response_ms');

export const options = {
  scenarios: {
    play: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      // 同时在途的请求数 ≈ 每秒请求数 × 响应时间。预先准备按 200ms 响应时间估算的 VU，最多允许到 1s。
      // 超出预先准备的数量时，k6 要在压测中途临时创建 VU，创建期间到点的请求发不出去；
      // VU 全部用完时同样发不出去。发不出的请求记为 dropped_iterations，报告里会体现
      preAllocatedVUs: Math.max(50, Math.ceil(RATE / 5)),
      maxVUs: Math.max(200, RATE),
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// 每个请求：随机挑一个视频，带随机的会话和 IP 调用播放统计接口，按返回结果计数
export default function () {
  const res = http.post(`${TARGET}/video/${pickVideoId()}?sessionId=${randomHex(32)}`, null, {
    headers: { 'X-Real-IP': randomIp() },
    tags: { name: 'playCount' },
  });
  lastResponse.add(Date.now());
  if (res.status !== 200) {
    fail(statusCause(res.status));
    return;
  }
  let body;
  try {
    body = res.json();
  } catch (e) {
    fail(failedBy.other);
    return;
  }
  if (body.status === 'success') {
    accepted.add(1);
    acceptedDuration.add(res.timings.duration);
  } else if (body.code === 429) {
    rejected.add(1);
  } else {
    fail(failedBy.other);
  }
}

// 失败计数：总数和对应原因各记一次
function fail(cause) {
  failed.add(1);
  cause.add(1);
}

// 状态码不是 200 时，按状态码找到对应原因的计数器
function statusCause(status) {
  if (status === 0) return failedBy.network;
  if (status === 502) return failedBy.status502;
  if (status >= 500) return failedBy.status5xx;
  if (status >= 400) return failedBy.status4xx;
  return failedBy.other;
}

// 测试结束后执行，此时所有请求都已返回。
// "压测结束时刻"取最后一个响应到达的时刻，而不是这里的当前时间：k6 收完最后一个响应后还要收尾、生成汇总，
// 这段时间请求越多越长，用当前时间会让写入完成延迟偏小
export function handleSummary(data) {
  const count = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
  // 响应时间的各个分位；一个都没记录过的指标（如没有放行的请求）在 data.metrics 里不存在，返回空对象
  const latency = (name) => {
    const d = data.metrics[name] ? data.metrics[name].values : null;
    return d ? { avg: d.avg, med: d.med, p90: d['p(90)'], p95: d['p(95)'], p99: d['p(99)'], max: d.max } : {};
  };
  const now = Date.now();
  const brief = {
    end_ms: data.metrics.last_response_ms ? Math.round(data.metrics.last_response_ms.values.max) : now,
    summary_ms: now, // 生成汇总的时刻，与 end_ms 的差就是 k6 收尾花的时间，留档备查
    rate: RATE,
    duration: DURATION,
    distribution: DISTRIBUTION,
    accepted: count('play_accepted'),
    rejected: count('play_rejected'),
    failed: count('play_failed'),
    failed_by_cause: {
      status_502: count('play_failed_502'),
      status_5xx_other: count('play_failed_5xx_other'),
      status_4xx: count('play_failed_4xx'),
      network: count('play_failed_network'),
      other: count('play_failed_other'),
    },
    requests: count('http_reqs'),
    dropped_iterations: count('dropped_iterations'),
    latency_ms: latency('http_req_duration'),               // 全部请求，包括失败的
    accepted_latency_ms: latency('play_accepted_duration'), // 只算放行的请求
  };
  return {
    [SUMMARY_FILE]: JSON.stringify(data, null, 2),
    [SUMMARY_FILE.replace('.json', '-brief.json')]: JSON.stringify(brief, null, 2),
    stdout: JSON.stringify(brief, null, 2) + '\n',
  };
}

// 视频 ID：uniform 在所有种子视频里均匀挑选；hot 有 80% 的概率落在前 1% 的视频上
function pickVideoId() {
  if (DISTRIBUTION === 'hot') {
    const hotCount = Math.max(1, Math.floor(VIDEO_COUNT / 100));
    return Math.random() < 0.8
      ? VIDEO_ID_BASE + randomInt(hotCount)
      : VIDEO_ID_BASE + hotCount + randomInt(VIDEO_COUNT - hotCount);
  }
  return VIDEO_ID_BASE + randomInt(VIDEO_COUNT);
}

function randomHex(len) {
  let s = '';
  for (let i = 0; i < len; i++) {
    s += randomInt(16).toString(16);
  }
  return s;
}

function randomIp() {
  return `10.${randomInt(256)}.${randomInt(256)}.${1 + randomInt(254)}`;
}

function randomInt(n) {
  return Math.floor(Math.random() * n);
}
