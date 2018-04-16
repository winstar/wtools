--
-- Created by IntelliJ IDEA.
-- User: winstar
-- Date: 2018/1/9
-- Time: 下午4:05
--


local _sp = redis.call('get', KEYS[1]..':sp');
local _mp = redis.call('get', KEYS[1]..':mp');
local _nf = redis.call('get', KEYS[1]..':nf');
local _si = redis.call('get', KEYS[1]..':si');

if (not _sp or not _mp or not _nf or not _si) then
    return nil;
end;

local sp = tonumber(_sp);
local mp = tonumber(_mp);
local nf = tonumber(_nf);
local si = tonumber(_si);

-- 变量
local now = tonumber(ARGV[1]);
local rp = tonumber(ARGV[2]);

if (now > nf) then
    sp = math.min((now - nf) / si + sp, mp);
    nf = now;
end;

local result = nf - now;

local spend = math.min(rp, sp);
local fresh = rp - spend;

if (fresh > 0) then
    nf = nf + fresh * si;
end;
sp = sp - spend;

redis.call('set', KEYS[1]..':sp', sp);
redis.call('set', KEYS[1]..':nf', nf);

return result;



---local _sp = redis.call('get', KEYS[1]..':sp');local _mp = redis.call('get', KEYS[1]..':mp');local _nf = redis.call('get', KEYS[1]..':nf');local _si = redis.call('get', KEYS[1]..':si');if (not _sp or not _mp or not _nf or not _si) then return nil;end;local sp = tonumber(_sp);local mp = tonumber(_mp);local nf = tonumber(_nf);local si = tonumber(_si);local now = tonumber(ARGV[1]);local rp = tonumber(ARGV[2]);if (now > nf) then sp = math.min((now - nf) / si + sp, mp);nf = now;end;local result = nf - now;local spend = math.min(rp, sp);local fresh = rp - spend;if (fresh > 0) then nf = nf + fresh * si;end;sp = sp - spend;redis.call('set', KEYS[1]..':sp', sp);redis.call('set', KEYS[1]..':nf', nf);return result;





