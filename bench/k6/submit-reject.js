// 基线 2：秒杀提交「拒绝路径」——伪造 path 的脚本直刷，应在 guard.lua 一次 Redis 往返内被拒（7003 / 4290 令牌桶）。
// 目标 ≥ 3000 QPS，p95 < 100ms，且没有任何请求越过 guard 进入扣库存（stock 不变）。
// RATE=3000 USERS=2000 DURATION=30s k6 run submit-reject.js
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { BASE, authed, body, login, pickActivity } from './lib.js';

const RATE = Number(__ENV.RATE || 3000);
const USERS = Number(__ENV.USERS || 2000);
const rejected = new Rate('rejected_ok');
const codes = new Counter('biz_code');

export const options = {
  setupTimeout: '120s',
  scenarios: {
    reject: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Math.min(USERS, Math.ceil(RATE / 4)),
      maxVUs: USERS,
    },
  },
  thresholds: {
    rejected_ok: ['rate>0.99'],
    'http_req_duration{name:submit}': ['p(95)<100'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  return { aid: pickActivity() };
}

let token = null; // 每个 VU 一个压测用户，首轮登录后复用

export default function (data) {
  if (!token) {
    token = login((exec.vu.idInTest - 1) % USERS);
    if (!token) return;
  }
  const r = http.post(`${BASE}/seckill/${data.aid}/forged${exec.scenario.iterationInTest}/orders`, '{}', authed(token, 'submit'));
  const b = body(r);
  const c = b ? b.code : -1;
  codes.add(1, { code: String(c) });
  rejected.add(c === 7003 || c === 4290);
  check(r, { 'rejected (7003/4290)': () => c === 7003 || c === 4290 });
}
