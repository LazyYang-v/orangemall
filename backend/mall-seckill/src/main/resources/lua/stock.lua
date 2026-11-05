-- 第三层：一人一单 + 原子扣减 + 记录在途/结果（一次往返）
-- KEYS: 1 stock  2 bought  3 inflight  4 metrics  5 result  6 path
-- ARGV: 1 userId  2 resultJson(QUEUING)  3 resultTtlSeconds
-- 返回 >=0 扣减后剩余；-1 已抢过(7004)；-2 未预热；-3 已抢完(7005)
if not redis.call('GET', KEYS[1]) then return -2 end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
  redis.call('HINCRBY', KEYS[4], 'repeatBlocked', 1)
  return -1
end
local left = redis.call('DECR', KEYS[1])
if left < 0 then
  redis.call('INCR', KEYS[1])
  redis.call('HINCRBY', KEYS[4], 'soldOut', 1)
  return -3
end
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('INCR', KEYS[3])
redis.call('HINCRBY', KEYS[4], 'enqueued', 1)
redis.call('SET', KEYS[5], ARGV[2], 'EX', tonumber(ARGV[3]))
if KEYS[6] then redis.call('DEL', KEYS[6]) end
return left
