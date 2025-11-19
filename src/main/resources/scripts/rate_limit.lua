-- Redis Token Bucket Rate Limiting Script
-- KEYS[1]: rate limit key (e.g., "rate_limit:user:123" or "rate_limit:ip:192.168.1.1")
-- ARGV[1]: bucket capacity (maximum tokens)
-- ARGV[2]: tokens per second (refill rate)
-- ARGV[3]: requested tokens (usually 1)
-- ARGV[4]: current timestamp in seconds

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local tokens_per_second = tonumber(ARGV[2])
local requested_tokens = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

-- Get current bucket state
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- Initialize bucket if it doesn't exist
if tokens == nil then
  tokens = capacity
  last_refill = now
end

-- Calculate tokens to add based on time elapsed
local time_elapsed = now - last_refill
local tokens_to_add = time_elapsed * tokens_per_second
tokens = math.min(capacity, tokens + tokens_to_add)
last_refill = now

-- Check if request can be allowed
local allowed = 0
if tokens >= requested_tokens then
  tokens = tokens - requested_tokens
  allowed = 1
end

-- Update bucket state
redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
-- Set expiration to 2 minutes (120 seconds)
redis.call('EXPIRE', key, 120)

-- Return: [allowed (1 or 0), remaining tokens]
return {allowed, tokens}

