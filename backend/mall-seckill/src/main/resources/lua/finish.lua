-- 消费结束（成功 / 失败补偿）：在途 -1，可选回补库存 / 移出已购集合，记指标，写结果
-- KEYS: 1 stock  2 bought  3 inflight  4 metrics  5 result
-- ARGV: 1 userId  2 resultJson  3 resultTtlSeconds  4 restoreStock(1/0)  5 removeBought(1/0)  6 metricField('' 表示不记)
if ARGV[4] == '1' and redis.call('EXISTS', KEYS[1]) == 1 then redis.call('INCR', KEYS[1]) end
if ARGV[5] == '1' then redis.call('SREM', KEYS[2], ARGV[1]) end
local v = redis.call('DECR', KEYS[3])
if v < 0 then redis.call('SET', KEYS[3], 0) end
if ARGV[6] ~= '' then redis.call('HINCRBY', KEYS[4], ARGV[6], 1) end
redis.call('SET', KEYS[5], ARGV[2], 'EX', tonumber(ARGV[3]))
return 1
