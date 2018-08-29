from unittest import TestCase
from experiment import BottleneckManager
import unittest
from configkeys import *
import tempfile
from os.path import join as pjoin
import requests
import shutil
import threading
import socketserver
import json
from http.server import HTTPServer, BaseHTTPRequestHandler, HTTPStatus
import mimetypes

@unittest.skip("showing class skipping")
class TestBottleneckManager(TestCase):
    def setUp(self):
        self.config = {K_STATE:tempfile.mkdtemp()}
        self.server1 = MockHttpServer()
        self.server2 = MockHttpServer()
        self.server1_url = "localhost:{}".format(self.server1.get_port())
        self.server2_url = "localhost:{}".format(self.server2.get_port())
        self.server1.start()
        self.server2.start()

        with open(pjoin(self.config[K_STATE], "bottleneck_1"), 'w') as f:
            f.write(self.server1_url)

        with open(pjoin(self.config[K_STATE], "bottleneck_2"), 'w') as f:
            f.write(self.server2_url)

    def tearDown(self):
        self.server1.stop()
        self.server2.stop()
        shutil.rmtree(self.config[K_STATE])

    def test_setup(self):
        ret = requests.get("http://" + self.server1_url)
        self.assertTrue(ret.ok)
        self.assertEqual(ret.status_code, 200)
        self.assertTrue("delay" in ret.text)

    def test_set_bottleneck(self):
        bm = BottleneckManager(self.config)
        before = self.server1.get_number_of_posts()
        bm.set_bottleneck(self.server1_url, 100)
        after = self.server1.get_number_of_posts()
        self.assertEqual(after, before+1)

    def test_read_bottlenecks(self):
        bm = BottleneckManager(self.config)
        ret = bm.read_bottlenecks(self.config)
        self.assertTrue("1" in ret)
        self.assertTrue("2" in ret)
        self.assertEqual(ret['1'], self.server1_url)
        self.assertEqual(ret['2'], self.server2_url)

    def test_validate_bottlenecks(self):
        bm = BottleneckManager(self.config)
        bn_addresses = {'1':self.server1_url, '2':self.server2_url,
                        '3':"localhost:11111"}
        bm.validate_bottlenecks(bn_addresses)
        self.assertTrue('1' in bn_addresses)
        self.assertTrue('2' in bn_addresses)
        self.assertTrue(not '3' in bn_addresses)

class MockHttpRequestHandler(BaseHTTPRequestHandler):
    gets_received = 0
    posts_received = 0

    def do_GET(self):
        MockHttpRequestHandler.gets_received += 1
        js = json.dumps({'delay':0})
        encoded = str.encode(js)
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-type", mimetypes.types_map['.json'])
        self.send_header("Content-Length", str(len(encoded)))
        #self.send_header("Last-Modified", self.date_time_string(fs.st_mtime))
        self.end_headers()
        self.wfile.write(encoded)
        self.wfile.flush()

    def do_POST(self):
        MockHttpRequestHandler.posts_received += 1
        self.send_response(HTTPStatus.OK)
        self.end_headers()

class MockHttpServer:
    def __init__(self):
        self.handler = MockHttpRequestHandler
        self.httpd = socketserver.TCPServer(("", 0), self.handler)

    def start(self):
        thread = threading.Thread(target=self.__run, args=())
        thread.daemon = True
        thread.start()

    def get_number_of_posts(self):
        return MockHttpRequestHandler.posts_received

    def get_number_of_gets(self):
        return MockHttpRequestHandler.gets_received

    def get_port(self):
        return self.httpd.server_address[1]

    def __run(self):
        self.httpd.serve_forever()

    def stop(self):
        self.httpd.shutdown()

if __name__ == '__main__':
    unittest.main()
