"""Fixed public APK distribution must never expose database objects or secrets."""
import hashlib
import pathlib
import socket
import sys
import unittest
import urllib.error
import urllib.request
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import test_cloud as cloud_helpers
ACCESS, ADMIN = cloud_helpers.ACCESS, cloud_helpers.ADMIN
from distribution import APK_OBJECT, PASSENGER_APK_OBJECT, APKStream, CHUNK_BYTES, GCSFixedAPK, MAX_APK_BYTES


class Blob:
    def __init__(self, data=b'PK-demo-apk', missing=False, size=None):
        self.data, self.missing = data, missing
        self.size = len(data) if size is None else size
        self.generation = 71
        self.ranges = []
        self.reloads = []
    def reload(self, **kw):
        self.reloads.append(kw)
        if self.missing:
            raise FileNotFoundError('missing fixed APK')
    def download_as_bytes(self, **kw):
        self.ranges.append(kw)
        return self.data[kw['start']:kw['end']+1]


class Bucket:
    def __init__(self, blob):
        self.value, self.names = blob, []
    def blob(self, name):
        self.names.append(name)
        return self.value


class DistributionIntegration(unittest.TestCase):
    setUp = cloud_helpers.CloudIntegration.setUp
    tearDown = cloud_helpers.CloudIntegration.tearDown
    start = cloud_helpers.CloudIntegration.start

    def test_passenger_distribution_is_fixed_and_separate(self):
        driver, passenger = Bucket(Blob(b'DRIVER')), Bucket(Blob(b'PASSENGER'))
        self.driver.apk_source=GCSFixedAPK(driver)
        self.driver.passenger_apk_source=GCSFixedAPK(passenger, passenger=True)
        _,page=self.get('/download-passenger')
        self.assertIn('/download-passenger.apk',page.decode())
        self.assertNotIn(ACCESS,page.decode())
        self.assertNotIn(ADMIN,page.decode())
        self.assertEqual(self.get('/download-passenger.apk')[1],b'PASSENGER')
        self.assertEqual(self.get('/download.apk')[1],b'DRIVER')
        self.assertEqual(passenger.names,[PASSENGER_APK_OBJECT])
        self.assertEqual(driver.names,[APK_OBJECT])
        self.get('/download-passenger.apk?path=demo/state.sqlite',400)
        self.get('/download-passenger.apk/other',404)
        self.get('/download-passenger',404,server=self.start('admin'))
        self.assertEqual(self.remote.reads,0)

    def get(self, path, expected=200, server=None, head=False):
        server = server or self.driver
        req = urllib.request.Request(f'http://127.0.0.1:{server.server_port}'+path,
                                    method='HEAD' if head else 'GET')
        try:
            r=urllib.request.urlopen(req,timeout=10)
        except urllib.error.HTTPError as exc:
            r=exc
        self.assertEqual(r.status,expected)
        return r.headers,r.read()

    def test_public_install_page_has_no_secret_and_never_touches_storage(self):
        self.remote.fail_read=True
        _,body=self.get('/download')
        text=body.decode()
        self.assertIn('/download.apk',text)
        self.assertIn('Google Cloud',text)
        self.assertNotIn(ACCESS,text)
        self.assertNotIn(ADMIN,text)
        self.assertEqual(self.remote.reads,0)
        self.assertIsNone(self.driver.service)

    def test_fixed_apk_stream_is_bounded_and_generation_pinned(self):
        data=b'P'*(CHUNK_BYTES*2+31)
        blob=Blob(data)
        bucket=Bucket(blob)
        self.driver.apk_source=GCSFixedAPK(bucket)
        headers,body=self.get('/download.apk')
        self.assertEqual(body,data)
        self.assertEqual(headers['Content-Type'],'application/vnd.android.package-archive')
        self.assertIn('attachment',headers['Content-Disposition'])
        self.assertEqual(headers['Transfer-Encoding'],'chunked')
        self.assertIsNone(headers.get('Content-Length'))
        self.assertEqual(bucket.names,[APK_OBJECT])
        self.assertEqual(len(blob.ranges),3)
        self.assertTrue(all(r['end']-r['start']+1<=CHUNK_BYTES for r in blob.ranges))
        self.assertTrue(all(r['if_generation_match']==71 and r['retry'] is None and r['timeout']==15 for r in blob.ranges))
        self.assertEqual(self.remote.reads,0)
        self.assertIsNone(self.driver.service)

    def test_no_object_path_query_or_path_traversal(self):
        bucket=Bucket(Blob())
        self.driver.apk_source=GCSFixedAPK(bucket)
        for path in ['/download.apk?object=demo/state.sqlite','/download?url=https://example.invalid',
                     '/download.apk?path=../../demo/state.sqlite']:
            self.get(path,400)
        self.get('/download.apk/../../demo/state.sqlite',404)
        self.assertEqual(bucket.names,[])
        self.assertEqual(self.remote.reads,0)

    def test_admin_role_does_not_expose_distribution(self):
        admin=self.start('admin')
        self.get('/download',404,server=admin)
        self.get('/download.apk',404,server=admin)
        self.assertEqual(self.remote.reads,0)

    def test_missing_or_oversized_apk_is_friendly_and_never_touches_db(self):
        for blob in [Blob(missing=True),Blob(size=MAX_APK_BYTES+1)]:
            bucket=Bucket(blob)
            self.driver.apk_source=GCSFixedAPK(bucket)
            headers,body=self.get('/download.apk',503)
            self.assertIn('安装包暂未准备好',body.decode())
            self.assertEqual(blob.ranges,[])
        self.assertEqual(self.remote.reads,0)

    def test_real_http_53mib_chunk_framing_and_exact_decoded_hash(self):
        size=53*1024*1024+17
        block=b'Z'*CHUNK_BYTES
        class LargeSource:
            def prepare(self, head=False):
                def chunks():
                    for _ in range(53):
                        yield block
                    yield b'Y'*17
                return APKStream(size,() if head else chunks())
        self.driver.apk_source=LargeSource()
        digest=hashlib.sha256();decoded=0;frames=0
        with socket.create_connection(('127.0.0.1',self.driver.server_port),timeout=15) as sock:
            sock.sendall(b'GET /download.apk HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n')
            stream=sock.makefile('rb')
            self.assertEqual(stream.readline(),b'HTTP/1.1 200 OK\r\n')
            headers={}
            while (line:=stream.readline())!=b'\r\n':
                key,value=line.decode().split(':',1);headers[key.lower()]=value.strip()
            self.assertEqual(headers['transfer-encoding'],'chunked')
            self.assertNotIn('content-length',headers)
            while True:
                length=int(stream.readline().strip(),16)
                if length==0:
                    self.assertEqual(stream.read(2),b'\r\n')
                    self.assertEqual(stream.read(),b'')
                    break
                self.assertLessEqual(length,CHUNK_BYTES)
                data=stream.read(length)
                self.assertEqual(len(data),length)
                digest.update(data);decoded+=length;frames+=1
                self.assertEqual(stream.read(2),b'\r\n')
        expected=hashlib.sha256()
        for _ in range(53):expected.update(block)
        expected.update(b'Y'*17)
        self.assertEqual(decoded,size)
        self.assertEqual(frames,54)
        self.assertEqual(digest.digest(),expected.digest())
        self.assertEqual(self.remote.reads,0)

    def test_interrupted_source_has_no_successful_terminal_chunk(self):
        class BrokenSource:
            def prepare(self, head=False):
                def chunks():
                    yield b'partial'
                    raise RuntimeError('source interrupted')
                return APKStream(20,chunks())
        self.driver.apk_source=BrokenSource()
        with socket.create_connection(('127.0.0.1',self.driver.server_port),timeout=10) as sock:
            sock.sendall(b'GET /download.apk HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n')
            stream=sock.makefile('rb')
            self.assertEqual(stream.readline(),b'HTTP/1.1 200 OK\r\n')
            while stream.readline()!=b'\r\n':pass
            body=stream.read()
        self.assertEqual(body,b'7\r\npartial\r\n')
        self.assertFalse(body.endswith(b'0\r\n\r\n'))
        self.assertEqual(self.remote.reads,0)

    def test_head_reads_only_apk_metadata_and_install_head_no_storage(self):
        blob=Blob()
        bucket=Bucket(blob)
        self.driver.apk_source=GCSFixedAPK(bucket)
        _,body=self.get('/download',head=True)
        self.assertEqual(body,b'')
        self.assertEqual(bucket.names,[])
        headers,body=self.get('/download.apk',head=True)
        self.assertEqual(body,b'')
        self.assertEqual(int(headers['Content-Length']),len(blob.data))
        self.assertEqual(bucket.names,[APK_OBJECT])
        self.assertEqual(blob.ranges,[])
        self.assertEqual(self.remote.reads,0)


if __name__=='__main__':
    unittest.main()
