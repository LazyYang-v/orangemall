/**
 * 浏览器端雪花算法（仅 Mock 使用；真实环境由订单服务生成）。
 * 41bit 时间戳 | 5bit 数据中心 | 5bit 机器 | 12bit 序列，返回字符串避免精度丢失。
 */
const EPOCH = 1735689600000n // 2025-01-01
let lastTs = -1n
let seq = 0n
const DATACENTER = 1n
const WORKER = 7n

export function snowflakeId(): string {
  let ts = BigInt(Date.now())
  if (ts === lastTs) {
    seq = (seq + 1n) & 0xfffn
    if (seq === 0n) {
      while (ts <= lastTs) ts = BigInt(Date.now())
    }
  } else {
    seq = 0n
  }
  lastTs = ts
  return (((ts - EPOCH) << 22n) | (DATACENTER << 17n) | (WORKER << 12n) | seq).toString()
}
