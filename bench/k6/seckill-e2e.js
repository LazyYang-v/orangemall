// 真实链路：5000 个不同用户（各自登录）→ 取 path → 提交 → 轮询结果。
// 链路：网关 JWT → guard.lua → stock.lua → Kafka → 消费者 DB 乐观锁 + 一人一单唯一键 → Feign 建单 → 结果回写。
// 验收（由 bench/verify.sh 在 DB/Redis 侧核对，k6 只统计用户视角）：
//   成功数 == 库存消耗 == seckill_order 行数 == mall_order 订单数，Redis/DB 库存 ≥ 0 且一致，无用户成交 2 单。
// USERS=5000 VUS=1000 AID=<活动> k6 run seckill-e2e.js
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE, authed, body, login, pickActivity } from './lib.js';

const USERS = Number(__ENV.USERS || 5000);
const VUS = Number(__ENV.VUS || 1000);
const OFFSET = Number(__ENV.USER_OFFSET || 0);
// POLL=0：只提交不轮询结果（配合 chain-throughput.sh：先暂停消费者积压，再恢复测纯落单吞吐）
const POLL = __ENV.POLL !== '0';

const outcome = new Counter('outcome');
const success = new Counter('orders_success');
const queued = new Counter('orders_queued');
const soldOut = new Counter('sold_out');
const errors = new Counter('unexpected');
const toResult = new Trend('submit_to_success_ms', true);

export const options = {
  setupTimeout: '60s',
  scenarios: {
    rush: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: USERS,
      maxDuration: __ENV.MAX_DURATION || '5m',
    },
  },
  thresholds: {
    unexpected: ['count==0'],
    'http_req_duration{name:submit}': ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  return { aid: pickActivity() };
}

export default function (data) {
  const uid = OFFSET + exec.scenario.iterationInTest;
  const token = login(uid);
  if (!token) {
    errors.add(1, { step: 'login' });
    outcome.add(1, { r: 'login_fail' });
    return;
  }
  const p = body(http.get(`${BASE}/seckill/${data.aid}/path`, authed(token, 'path')));
  if (!p || p.code !== 0) {
    errors.add(1, { step: 'path', code: String(p ? p.code : -1) });
    outcome.add(1, { r: 'path_' + (p ? p.code : -1) });
    return;
  }
  const t0 = Date.now();
  const s = body(http.post(`${BASE}/seckill/${data.aid}/${p.data.path}/orders`, '{}', authed(token, 'submit')));
  const c = s ? s.code : -1;
  if (c === 7005) {
    soldOut.add(1);
    outcome.add(1, { r: 'sold_out' });
    return;
  }
  if (c !== 0) {
    // 4290（单用户令牌桶）在每人只点一次的场景下不应出现，视为异常
    errors.add(1, { step: 'submit', code: String(c) });
    outcome.add(1, { r: 'submit_' + c });
    return;
  }
  if (!POLL) {
    queued.add(1);
    outcome.add(1, { r: 'queued' });
    return;
  }
  // 排队中：轮询结果，最长 60s
  for (let i = 0; i < 120; i++) {
    sleep(0.5);
    const r = body(http.get(`${BASE}/seckill/${data.aid}/result`, authed(token, 'result')));
    const st = r && r.data ? r.data.state : null;
    if (st === 'SUCCESS') {
      success.add(1);
      toResult.add(Date.now() - t0);
      outcome.add(1, { r: 'success' });
      check(r, { 'has orderId': () => !!r.data.orderId });
      return;
    }
    if (st === 'FAILED') {
      // 消费侧失败（如 DB 售罄兜底）会回补 Redis，属于合法结局，单独计数
      outcome.add(1, { r: 'failed_' + (r.data.reason || '') });
      return;
    }
  }
  errors.add(1, { step: 'result_timeout' });
  outcome.add(1, { r: 'result_timeout' });
}
