// 播放统计接口压测（k6），在压测机上运行，通过 Tailscale 隧道访问被测机。
//
// k6 按 options 里的设定，每秒调用 RATE 次下面的 default 函数（每次就是一个请求），
// 全部结束后调用 handleSummary 输出结果。
//
// 环境变量：
//   TARGET         被测机 nilo-web 地址，如 http://100.x.y.z:7071
//   RATE           每秒请求数（固定到达率：不受响应快慢影响，按时发出）
//   DURATION       持续时间，如 3m
//   VIDEO_COUNT    种子视频数量，视频ID从 VIDEO_ID_BASE 开始连续编号
//   VIDEO_ID_BASE  种子视频的起始ID，与 sut/core/config.py 一致
//   DISTRIBUTION   uniform：均匀分布；hot：80% 的请求集中在 1% 的视频上
//   SUMMARY_FILE   结果文件名
//
// 每个请求带随机的 sessionId 和 X-Real-IP，模拟不同的用户，使请求都走放行路径（会话、IP 两个维度的限流都不会触发）。
import http from 'k6/http';
import { Counter } from 'k6/metrics';

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
  if (res.status !== 200) {
    failed.add(1);
    return;
  }
  let body;
  try {
    body = res.json();
  } catch (e) {
    failed.add(1);
    return;
  }
  if (body.status === 'success') {
    accepted.add(1);
  } else if (body.code === 429) {
    rejected.add(1);
  } else {
    failed.add(1);
  }
}

// 测试结束后执行：此时所有请求都已返回，这个时间点作为"压测结束时刻"
export function handleSummary(data) {
  const count = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
  const d = data.metrics.http_req_duration.values;
  const brief = {
    end_ms: Date.now(),
    rate: RATE,
    duration: DURATION,
    distribution: DISTRIBUTION,
    accepted: count('play_accepted'),
    rejected: count('play_rejected'),
    failed: count('play_failed'),
    requests: count('http_reqs'),
    dropped_iterations: count('dropped_iterations'),
    latency_ms: { avg: d.avg, med: d.med, p90: d['p(90)'], p95: d['p(95)'], p99: d['p(99)'], max: d.max },
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
