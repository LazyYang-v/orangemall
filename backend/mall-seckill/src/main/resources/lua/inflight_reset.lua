-- OM-013 在途滞留强制重置：只扣掉「判定为泄漏的那部分」在途，判定之后新进来的在途保留
-- KEYS: 1 stock  2 inflight
-- ARGV: 1 dbStock  2 leakedInflight（判定时观察到的滞留值）
-- 返回 {重置后在途, 重置后 Redis 库存}；库存 key 不存在（未预热）时只重置在途，库存返回 -1
local cur = tonumber(redis.call('GET', KEYS[2]) or '0')
local left = math.max(0, cur - tonumber(ARGV[2]))
redis.call('SET', KEYS[2], left)
if redis.call('EXISTS', KEYS[1]) == 0 then return {left, -1} end
local v = math.max(0, tonumber(ARGV[1]) - left)
redis.call('SET', KEYS[1], v)
return {left, v}
