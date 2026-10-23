import importlib.util
import pathlib
import threading
import unittest
import urllib.request
import urllib.error
from http.server import ThreadingHTTPServer

spec = importlib.util.spec_from_file_location('gateway', pathlib.Path(__file__).with_name('serve.py'))
gateway = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gateway)

class GatewayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), gateway.Gateway)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = 'http://127.0.0.1:' + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def fetch(self, path='/', headers=None, data=None):
        try:
            return urllib.request.urlopen(urllib.request.Request(self.url + path, headers=headers or {}, data=data))
        except urllib.error.HTTPError as error:
            return error

    def test_static_console(self):
        with self.fetch() as response:
            self.assertEqual(response.status, 200)
            self.assertIn('no-store', response.headers['Cache-Control'])
            self.assertIn('frame-ancestors', response.headers['Content-Security-Policy'])

    def test_dns_rebinding_rejected(self):
        with self.fetch(headers={'Host': 'attacker.example'}) as response:
            self.assertEqual(response.status, 403)

    def test_cross_origin_rejected(self):
        with self.fetch(headers={'Origin': 'https://attacker.example'}) as response:
            self.assertEqual(response.status, 403)

    def test_no_arbitrary_files(self):
        with self.fetch('/../.private/admin-token.txt') as response:
            self.assertEqual(response.status, 404)

    def test_form_post_rejected(self):
        with self.fetch('/api/local/admin/v2/action', data=b'action=x') as response:
            self.assertEqual(response.status, 415)

if __name__ == '__main__':
    unittest.main()
