-- 以 DB 为准回写 Redis 库存：Redis = DB - 在途
-- KEYS: 1 stock  2 inflight
-- ARGV: 1 dbStock  2 requireIdle(1 = 在途必须为 0，否则放弃)
-- 返回 {0, 写入值} 或 {1, 当前在途数}（放弃）
local inf = tonumber(redis.call('GET', KEYS[2]) or '0')
if ARGV[2] == '1' and inf ~= 0 then return {1, inf} end
local v = math.max(0, tonumber(ARGV[1]) - inf)
redis.call('SET', KEYS[1], v)
return {0, v}
