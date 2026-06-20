-- rate_limit.lua — 3-layer atomic rate limit check
-- KEYS[1]: QPS key        omni:rate:qps:{tenantId}
-- KEYS[2]: Budget key     omni:rate:budget:{tenantId}
-- KEYS[3]: Concurrent key omni:rate:concurrent:{tenantId}
-- ARGV[1]: QPS limit
-- ARGV[2]: Monthly token budget (0 = unlimited)
-- ARGV[3]: Estimated tokens for this request
-- ARGV[4]: Concurrent call limit
-- ARGV[5]: QPS window TTL in seconds
-- ARGV[6]: Budget key TTL in seconds
-- Returns: {allowed (1|0), reject_reason}

local qps_key     = KEYS[1]
local budget_key  = KEYS[2]
local conc_key    = KEYS[3]

local qps_limit      = tonumber(ARGV[1])
local monthly_budget = tonumber(ARGV[2])
local est_tokens     = tonumber(ARGV[3])
local conc_limit     = tonumber(ARGV[4])
local qps_ttl        = tonumber(ARGV[5])
local budget_ttl     = tonumber(ARGV[6])

-- Layer 1: QPS check
if qps_limit > 0 then
    local current_qps = redis.call('INCR', qps_key)
    if current_qps == 1 then
        redis.call('EXPIRE', qps_key, qps_ttl)
    end
    if current_qps > qps_limit then
        return {0, 'QPS_LIMITED'}
    end
end

-- Layer 2: Monthly budget check
if monthly_budget > 0 then
    local current_usage = redis.call('GET', budget_key)
    if current_usage then
        if tonumber(current_usage) + est_tokens > monthly_budget then
            return {0, 'BUDGET_EXCEEDED'}
        end
    end
end

-- Layer 3: Concurrent call check
if conc_limit > 0 then
    local current_conc = redis.call('INCR', conc_key)
    if current_conc > conc_limit then
        redis.call('DECR', conc_key)
        return {0, 'CONCURRENT_LIMITED'}
    end
end

return {1, 'OK'}
