"""Read-only public distribution of one fixed, secret-free APK object."""
from dataclasses import dataclass
from typing import Iterable

APK_OBJECT = 'distribution/yijiexing-cloud.apk'
PASSENGER_APK_OBJECT = 'distribution/yijiexing-passenger.apk'
MAX_APK_BYTES = 128 * 1024 * 1024
CHUNK_BYTES = 1024 * 1024


class APKUnavailable(Exception):
    pass


@dataclass(frozen=True)
class APKStream:
    size: int
    chunks: Iterable[bytes]


class GCSFixedAPK:
    def __init__(self, bucket, passenger=False):
        self.bucket = bucket
        self.object_name = PASSENGER_APK_OBJECT if passenger else APK_OBJECT

    def prepare(self, head=False):
        try:
            blob = self.bucket.blob(self.object_name)
            blob.reload(timeout=8, retry=None)
            size = int(blob.size)
            generation = int(blob.generation)
            if not 0 < size <= MAX_APK_BYTES:
                raise APKUnavailable('APK outside size limit')
            if head:
                return APKStream(size, ())
            # Preload the first bounded chunk before sending HTTP headers. Pin
            # every read to this object generation so a replacement never mixes
            # bytes from different APK versions. No user path/URL is accepted.
            first = blob.download_as_bytes(start=0, end=min(CHUNK_BYTES, size)-1,
                if_generation_match=generation, timeout=15, retry=None)
            if len(first) != min(CHUNK_BYTES, size):
                raise APKUnavailable('incomplete APK first chunk')
            def chunks():
                yield first
                for start in range(len(first), size, CHUNK_BYTES):
                    end = min(start+CHUNK_BYTES, size)-1
                    data = blob.download_as_bytes(start=start, end=end,
                        if_generation_match=generation, timeout=15, retry=None)
                    if len(data) != end-start+1:
                        raise APKUnavailable('incomplete APK chunk')
                    yield data
            return APKStream(size, chunks())
        except APKUnavailable:
            raise
        except Exception as exc:
            raise APKUnavailable('fixed APK object unavailable') from exc


INSTALL_HTML = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>宜捷行安卓体验版</title><style>body{font-family:system-ui,sans-serif;margin:0;background:#f4f7fb;color:#17263b}main{max-width:480px;margin:6vh auto;padding:28px;background:white;border-radius:20px}h1{font-size:27px}p,li{line-height:1.8;color:#526174}a{display:block;text-align:center;padding:15px;background:#007f76;color:white;text-decoration:none;border-radius:12px;font-weight:650}small{display:block;line-height:1.7;margin-top:22px;color:#64748b}@media(max-width:540px){main{margin:20px;padding:24px}}</style><main><h1>宜捷行安卓体验版</h1><p>原生安卓端 · 独立云演示环境</p><a href="/download.apk" download="yijiexing-cloud.apk">下载安卓安装包</a><ol><li>请在安卓手机浏览器中打开此页并下载。</li><li>下载后打开安装包，按系统提示允许本次安装。支持 Android 8.0 及以上。</li><li>打开 App，选择体验司机账号，阅读并勾选体验说明后点击“进入体验”。</li></ol><small>资料、定位与附件保存在 Google Cloud 独立演示服务。订单与金额为演示数据；支付、提现不会真实收款或银行打款。体验账号共用演示数据，后台管理仍需独立登录。</small></main></html>'''
UNAVAILABLE_HTML = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>安装包准备中</title><main style="font-family:system-ui;margin:10vh auto;max-width:440px;padding:24px"><h1>安装包暂未准备好</h1><p>请稍后重试，或联系管理员确认最新体验版本。</p><p><a href="/download">返回安装说明</a></p></main></html>'''

PASSENGER_INSTALL_HTML = (INSTALL_HTML
    .replace('宜捷行安卓体验版', '宜捷行乘客端')
    .replace('/download.apk', '/download-passenger.apk')
    .replace('yijiexing-cloud.apk', 'yijiexing-passenger.apk')
    .replace('原生安卓端 · 独立云演示环境', '乘客原生安卓端 · 与司机共享同一订单')
    .replace('选择体验司机账号', '选择体验乘客账号')
    .replace('订单与金额为演示数据；支付、提现不会真实收款或银行打款。', '乘客实际下单，与司机端同步订单；金额为体验估价，支付与退款均为明确标识的模拟操作，不收取真实费用。地图与路线仅供体验。'))
