"""Independent synthetic cloud demo model; no original backend configuration is imported."""
import base64, copy, datetime as dt, hashlib, hmac, json, os, re, secrets
TZ = dt.timezone(dt.timedelta(hours=8))
def now(): return dt.datetime.now(TZ)
def stamp(t=None): return (t or now()).isoformat(timespec='seconds')
def parse_time(v):
    try:
        t=dt.datetime.fromisoformat(str(v).replace('Z','+00:00'))
        if t.tzinfo is None: raise ValueError()
        return t
    except (ValueError,TypeError): raise ApiError(400, '时间格式须为带时区的 ISO8601')
def uid(prefix): return prefix + secrets.token_hex(6)
def dumps(v): return json.dumps(v,ensure_ascii=False,separators=(',',':'))
def clone(v): return copy.deepcopy(v)
class ApiError(Exception):
    def __init__(self,status,message,details=None,code=None):
        self.status,self.message,self.details,self.code=status,message,details or {},code or status
        super().__init__(message)
def require(condition,message,status=400,details=None):
    if not condition: raise ApiError(status,message,details)
def cents(value,label='金额',allow_zero=False):
    require(isinstance(value,int) and not isinstance(value,bool),label+'必须使用整数分')
    require((0 if allow_zero else 1)<=value<=100000000,label+'超出云端演示允许范围')
    return value
def textfield(value,label,minlen=1,maxlen=500):
    require(isinstance(value,str) and minlen<=len(value.strip())<=maxlen,label+f'须为{minlen}至{maxlen}字')
    return value.strip()
def secret_hash(v,salt=None):
    salt=salt or secrets.token_hex(16)
    return salt+':'+hashlib.pbkdf2_hmac('sha256',str(v).encode(),salt.encode(),120000).hex()
def secret_check(v,stored):
    return bool(stored) and hmac.compare_digest(secret_hash(v,stored.split(':')[0]),stored)
def check_password(v):
    require(isinstance(v,str) and re.fullmatch(r'\d{6}',v),'交易密码须为6位数字')
    return v
# Harmless 1x1 PNG, used for emulator attachment tests only.
SAMPLE_IMAGE='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='
