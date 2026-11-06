// OM-025 压测公共函数。BASE 默认直连网关（compose 内网），绕开 nginx 的 seckill_ip 限流，测的是服务本身。
import http from 'k6/http';

export const BASE = __ENV.BASE || 'http://mall-gateway:8080/api';
export const JSON_H = { 'Content-Type': 'application/json' };

/** 压测用户 i（0..4999）的手机号，见 bench/sql/seed-bench-users.sql */
export function phone(i) {
  return '177' + String(i).padStart(8, '0');
}

export function login(i) {
  const r = http.post(`${BASE}/auth/login`, JSON.stringify({ phone: phone(i), password: '123456' }), {
    headers: JSON_H,
    tags: { name: 'login' },
  });
  const b = body(r);
  return b && b.code === 0 ? b.data.token : null;
}

export function authed(token, name) {
  return { headers: { ...JSON_H, Authorization: `Bearer ${token}` }, tags: { name } };
}

/** Result 恒为 HTTP 200，业务成败看 code */
export function body(r) {
  try {
    return r.json();
  } catch (e) {
    return null;
  }
}

/** 活动：优先 env AID；否则取进行中场次里剩余库存最多的活动 */
export function pickActivity() {
  if (__ENV.AID) return __ENV.AID;
  const b = body(http.get(`${BASE}/seckill/sessions`));
  const ongoing = (b && b.data ? b.data : []).filter((s) => s.status === 'ONGOING');
  const items = ongoing.flatMap((s) => s.items).sort((a, b) => b.remainStock - a.remainStock);
  if (!items.length) throw new Error('当前没有进行中的秒杀场次');
  return items[0].activityId;
}
