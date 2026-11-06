// 基线 1：秒杀场次列表（匿名、读多写零，走本地/Redis 缓存）。目标 ≥ 1500 QPS，p95 < 200ms。
// RATE=1500 DURATION=30s k6 run sessions.js
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE, body } from './lib.js';

const bizOk = new Rate('biz_ok');
const RATE = Number(__ENV.RATE || 1500);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    sessions: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Math.ceil(RATE / 5),
      maxVUs: RATE,
    },
  },
  thresholds: {
    biz_ok: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200'],
    dropped_iterations: [`count<${Math.ceil(RATE * 0.01 * 30)}`],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const r = http.get(`${BASE}/seckill/sessions`, { tags: { name: 'sessions' } });
  const b = body(r);
  bizOk.add(!!b && b.code === 0);
  check(r, { 'code=0': () => !!b && b.code === 0 });
}
