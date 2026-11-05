-- 第一、二层：令牌桶限流 + 动态 path 校验 + 风控（一次往返）
-- KEYS: 1 bucket  2 metrics  3 path  4 risk:credit(ZSET)  5 risk:blocked(SET)
-- ARGV: 1 nowMs  2 capacity  3 rate(/s)  4 path  5 userId  6 minCredit
-- 返回 0 通过；1 限流(4290)；2 path 无效(7003)；3 风控(7009)
redis.call('HINCRBY', KEYS[2], 'totalRequests', 1)
local now = tonumber(ARGV[1])
local cap = tonumber(ARGV[2])
local rate = tonumber(ARGV[3])
local b = redis.call('HMGET', KEYS[1], 't', 'ts')
local tokens = tonumber(b[1])
local ts = tonumber(b[2])
if tokens == nil or ts == nil then
  tokens = cap
  ts = now
end
if now > ts then
  tokens = math.min(cap, tokens + (now - ts) / 1000 * rate)
  ts = now
end
local ok = tokens >= 1
if ok then tokens = tokens - 1 end
redis.call('HSET', KEYS[1], 't', tostring(tokens), 'ts', tostring(ts))
redis.call('PEXPIRE', KEYS[1], math.ceil(cap / rate * 1000) + 1000)
if not ok then
  redis.call('HINCRBY', KEYS[2], 'rateLimited', 1)
  return 1
end
local p = redis.call('GET', KEYS[3])
if (not p) or p ~= ARGV[4] then
  redis.call('HINCRBY', KEYS[2], 'illegalPath', 1)
  return 2
end
local s = redis.call('ZSCORE', KEYS[4], ARGV[5])
if s and tonumber(s) < tonumber(ARGV[6]) then return 3 end
if redis.call('SISMEMBER', KEYS[5], ARGV[5]) == 1 then return 3 end
return 0
