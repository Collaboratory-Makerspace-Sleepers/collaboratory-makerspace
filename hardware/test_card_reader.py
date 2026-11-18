"""Standalone reader checks; no USB reader or real backend is contacted."""
import importlib.util
import io
import json
import sys
import unittest
import urllib.error
from pathlib import Path
from types import ModuleType
from unittest.mock import Mock, patch


class CardReaderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        smartcard = ModuleType("smartcard")
        system = ModuleType("smartcard.System")
        system.readers = Mock(return_value=[])
        spec = importlib.util.spec_from_file_location(
            "standalone_card_reader", Path(__file__).with_name("card_reader.py")
        )
        cls.reader = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {"smartcard": smartcard, "smartcard.System": system}):
            spec.loader.exec_module(cls.reader)

    def test_uid_preserves_leading_zero_and_sends_correct_command(self):
        connection = Mock()
        connection.transmit.return_value = ([0, 17, 34, 171], 0x90, 0)
        self.assertEqual(self.reader.read_uid(connection), "001122AB")
        connection.transmit.assert_called_once_with([0xFF, 0xCA, 0, 0, 0])

    def test_reader_failure_status_is_not_a_uid(self):
        connection = Mock()
        connection.transmit.return_value = ([], 0x63, 0)
        with self.assertRaisesRegex(RuntimeError, "6300"):
            self.reader.read_uid(connection)

    def test_empty_uid_is_not_a_valid_tap(self):
        connection = Mock()
        connection.transmit.return_value = ([], 0x90, 0)
        with self.assertRaises(RuntimeError):
            self.reader.read_uid(connection)

    def test_scan_payload_device_key_timeout_and_response(self):
        response = Mock()
        response.__enter__ = Mock(return_value=io.StringIO(
            '{"approved":true,"message":"Access granted","memberName":"Test"}'
        ))
        response.__exit__ = Mock(return_value=False)
        with patch.object(self.reader, "BACKEND_URL", "http://127.0.0.1:8080"), \
                patch.object(self.reader, "DEVICE_ID", "test-reader"), \
                patch.object(self.reader, "DEVICE_KEY", "test-device-key"), \
                patch.object(self.reader.urllib.request, "urlopen", return_value=response) as send:
            self.assertIs(self.reader.send_scan("001122AB")["approved"], True)
        request = send.call_args.args[0]
        self.assertEqual(request.full_url, "http://127.0.0.1:8080/api/v1/hardware/card-scan")
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(json.loads(request.data), {"cardUid": "001122AB", "deviceId": "test-reader"})
        self.assertEqual(request.get_header("X-device-key"), "test-device-key")
        self.assertEqual(send.call_args.kwargs["timeout"], 5)

    def test_malformed_responses_never_approve_access(self):
        for body in ['{"approved":"false"}', '[]', 'not json', '{"approved":true}', 'null']:
            with self.subTest(body=body):
                response = Mock()
                response.__enter__ = Mock(return_value=io.StringIO(body))
                response.__exit__ = Mock(return_value=False)
                with patch.object(self.reader.urllib.request, "urlopen", return_value=response):
                    self.assertIs(self.reader.send_scan("001122AB")["approved"], False)

    def test_backend_or_network_failure_is_not_approval(self):
        for error in [
            urllib.error.HTTPError("http://localhost", 401, "Unauthorized", {}, None),
            urllib.error.URLError("offline"),
            TimeoutError("timed out"),
        ]:
            with self.subTest(error=type(error).__name__):
                with patch.object(self.reader.urllib.request, "urlopen", side_effect=error):
                    self.assertIs(self.reader.send_scan("001122AB")["approved"], False)

    def test_missing_reader_exits_with_actionable_message(self):
        with patch.object(self.reader, "readers", return_value=[]):
            with self.assertRaisesRegex(SystemExit, "No NFC reader found"):
                self.reader.main()

    def test_held_card_submits_once_until_removed(self):
        connection = Mock()
        device = Mock()
        device.createConnection.return_value = connection
        with patch.object(self.reader, "readers", return_value=[device]), \
                patch.object(self.reader, "read_uid", side_effect=[
                    "001122AB", "001122AB", RuntimeError("removed"), "001122AB"
                ]), \
                patch.object(self.reader, "send_scan", return_value={"approved": False}) as send, \
                patch.object(self.reader.time, "sleep", side_effect=[None, None, None, KeyboardInterrupt]), \
                patch("builtins.print"):
            with self.assertRaises(KeyboardInterrupt):
                self.reader.main()
        self.assertEqual(send.call_count, 2)


if __name__ == "__main__":
    unittest.main()
